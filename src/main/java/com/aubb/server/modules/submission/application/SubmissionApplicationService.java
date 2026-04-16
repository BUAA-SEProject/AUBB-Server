package com.aubb.server.modules.submission.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.common.storage.ObjectStorageException;
import com.aubb.server.common.storage.ObjectStorageService;
import com.aubb.server.common.storage.StoredObject;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.JudgeApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService.PersistedStructuredAnswers;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerInput;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerView;
import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import com.aubb.server.modules.submission.domain.SubmissionStatus;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SubmissionApplicationService {

    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final long MAX_ARTIFACT_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_ARTIFACTS_PER_SUBMISSION = 10;
    private static final int MAX_STORAGE_FILENAME_LENGTH = 80;
    private static final String OBJECT_KEY_PREFIX = "submission-artifacts";

    private final SubmissionMapper submissionMapper;
    private final SubmissionArtifactMapper submissionArtifactMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final SubmissionAnswerApplicationService submissionAnswerApplicationService;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectProvider<ObjectStorageService> objectStorageServiceProvider;
    private final JudgeApplicationService judgeApplicationService;

    @Transactional
    public SubmissionArtifactView uploadArtifact(
            Long assignmentId, MultipartFile file, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        OffsetDateTime now = OffsetDateTime.now();
        assertCanPrepareSubmission(principal, assignment, now);
        assertSubmissionAttemptsAvailable(assignment, principal.getUserId());

        byte[] content = readArtifactContent(file);
        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String objectKey = generateArtifactObjectKey(assignmentId, principal.getUserId(), originalFilename);

        ObjectStorageService storageService = requireStorageService();
        try {
            storageService.putObject(objectKey, content, contentType);
        } catch (ObjectStorageException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "SUBMISSION_STORAGE_WRITE_FAILED", "提交附件暂时不可写入");
        }

        SubmissionArtifactEntity entity = new SubmissionArtifactEntity();
        entity.setAssignmentId(assignmentId);
        entity.setOfferingId(assignment.getOfferingId());
        entity.setTeachingClassId(assignment.getTeachingClassId());
        entity.setUploaderUserId(principal.getUserId());
        entity.setObjectKey(objectKey);
        entity.setOriginalFilename(originalFilename);
        entity.setContentType(contentType);
        entity.setSizeBytes((long) content.length);
        entity.setUploadedAt(now);
        try {
            submissionArtifactMapper.insert(entity);
        } catch (RuntimeException exception) {
            safeDeleteObject(objectKey);
            throw exception;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignmentId);
        metadata.put("offeringId", assignment.getOfferingId());
        metadata.put("sizeBytes", entity.getSizeBytes());
        metadata.put("originalFilename", originalFilename);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.SUBMISSION_ARTIFACT_UPLOADED,
                "SUBMISSION_ARTIFACT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                metadata);
        return toArtifactView(entity);
    }

    @Transactional
    public SubmissionView createSubmission(
            Long assignmentId,
            String contentText,
            List<Long> artifactIds,
            List<SubmissionAnswerInput> answerInputs,
            AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        OffsetDateTime now = OffsetDateTime.now();
        assertCanPrepareSubmission(principal, assignment, now);
        boolean structuredAssignment = assignmentPaperApplicationService.hasStructuredPaper(assignmentId);
        List<SubmissionArtifactEntity> artifacts;
        String normalizedContent;
        if (structuredAssignment) {
            validateStructuredSubmissionEnvelope(contentText, artifactIds);
            List<Long> normalizedStructuredArtifactIds =
                    normalizeArtifactIds(extractStructuredArtifactIds(answerInputs));
            artifacts = loadAttachableArtifacts(assignmentId, principal.getUserId(), normalizedStructuredArtifactIds);
            normalizedContent = null;
        } else {
            validateLegacySubmissionEnvelope(answerInputs);
            List<Long> normalizedArtifactIds = normalizeArtifactIds(artifactIds);
            normalizedContent = normalizeContentText(contentText, !normalizedArtifactIds.isEmpty());
            artifacts = loadAttachableArtifacts(assignmentId, principal.getUserId(), normalizedArtifactIds);
        }

        int nextAttempt = countAttempts(assignmentId, principal.getUserId()) + 1;
        if (nextAttempt > assignment.getMaxSubmissions()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_LIMIT_REACHED", "已达到当前作业最大提交次数");
        }

        SubmissionEntity entity = new SubmissionEntity();
        entity.setSubmissionNo(generateSubmissionNo());
        entity.setAssignmentId(assignmentId);
        entity.setOfferingId(assignment.getOfferingId());
        entity.setTeachingClassId(assignment.getTeachingClassId());
        entity.setSubmitterUserId(principal.getUserId());
        entity.setAttemptNo(nextAttempt);
        entity.setStatus(SubmissionStatus.SUBMITTED.name());
        entity.setContentText(normalizedContent);
        entity.setSubmittedAt(now);
        submissionMapper.insert(entity);

        attachArtifacts(entity.getId(), artifacts);
        if (structuredAssignment) {
            PersistedStructuredAnswers persistedAnswers = submissionAnswerApplicationService.persistStructuredAnswers(
                    entity,
                    answerInputs,
                    artifacts.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    SubmissionArtifactEntity::getId,
                                    artifact -> artifact,
                                    (left, right) -> left,
                                    LinkedHashMap::new)));
            judgeApplicationService.enqueueProgrammingJudges(entity, assignment, persistedAnswers);
        } else {
            judgeApplicationService.enqueueAutoJudge(entity, assignment);
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.SUBMISSION_CREATED,
                "SUBMISSION",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "assignmentId",
                        assignmentId,
                        "offeringId",
                        assignment.getOfferingId(),
                        "attemptNo",
                        nextAttempt,
                        "artifactCount",
                        artifacts.size(),
                        "structuredAnswerCount",
                        structuredAssignment && answerInputs != null ? answerInputs.size() : 0));
        boolean gradePublished = assignment.getGradePublishedAt() != null;
        return toView(entity, assignment, toArtifactViews(artifacts), gradePublished, gradePublished);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionView> listMySubmissions(
            Long assignmentId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该作业提交");
        }
        List<SubmissionEntity> matched = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                .eq(SubmissionEntity::getSubmitterUserId, principal.getUserId())
                .orderByDesc(SubmissionEntity::getSubmittedAt)
                .orderByDesc(SubmissionEntity::getId));
        boolean gradePublished = assignment.getGradePublishedAt() != null;
        return toPage(matched, assignment, page, pageSize, gradePublished, gradePublished);
    }

    @Transactional(readOnly = true)
    public SubmissionView getMySubmission(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (!Objects.equals(submission.getSubmitterUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该提交");
        }
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该提交");
        }
        boolean gradePublished = assignment.getGradePublishedAt() != null;
        return toView(submission, assignment, loadArtifactViews(submissionId), gradePublished, gradePublished);
    }

    @Transactional(readOnly = true)
    public SubmissionArtifactDownload downloadMyArtifact(Long artifactId, AuthenticatedUserPrincipal principal) {
        SubmissionArtifactEntity artifact = requireArtifact(artifactId);
        AssignmentEntity assignment = requireAssignment(artifact.getAssignmentId());
        if (!Objects.equals(artifact.getUploaderUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权下载该附件");
        }
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权下载该附件");
        }
        return loadStoredArtifact(artifact);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionView> listTeacherSubmissions(
            Long assignmentId,
            Long submitterUserId,
            boolean latestOnly,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        List<SubmissionEntity> matched = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                .eq(submitterUserId != null, SubmissionEntity::getSubmitterUserId, submitterUserId)
                .orderByDesc(SubmissionEntity::getSubmittedAt)
                .orderByDesc(SubmissionEntity::getId));
        if (latestOnly) {
            matched = loadLatestSubmissions(matched);
        }
        return toPage(matched, assignment, page, pageSize, true, assignment.getGradePublishedAt() != null);
    }

    @Transactional(readOnly = true)
    public SubmissionView getTeacherSubmission(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        return toView(
                submission,
                assignment,
                loadArtifactViews(submissionId),
                true,
                assignment.getGradePublishedAt() != null);
    }

    @Transactional(readOnly = true)
    public SubmissionArtifactDownload downloadTeacherArtifact(Long artifactId, AuthenticatedUserPrincipal principal) {
        SubmissionArtifactEntity artifact = requireArtifact(artifactId);
        AssignmentEntity assignment = requireAssignment(artifact.getAssignmentId());
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        if (artifact.getSubmissionId() == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_ARTIFACT_NOT_FOUND", "提交附件不存在");
        }
        return loadStoredArtifact(artifact);
    }

    private PageResponse<SubmissionView> toPage(
            List<SubmissionEntity> entities,
            AssignmentEntity assignment,
            long page,
            long pageSize,
            boolean revealNonObjectiveScores,
            boolean gradePublished) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        List<SubmissionEntity> pageItems =
                entities.stream().skip(offset).limit(safePageSize).toList();
        Map<Long, List<SubmissionArtifactView>> artifactsBySubmissionId = loadArtifactViewsBySubmissionIds(
                pageItems.stream().map(SubmissionEntity::getId).toList());
        List<SubmissionView> items = pageItems.stream()
                .map(entity -> toView(
                        entity,
                        assignment,
                        artifactsBySubmissionId.getOrDefault(entity.getId(), List.of()),
                        revealNonObjectiveScores,
                        gradePublished))
                .toList();
        return new PageResponse<>(items, entities.size(), safePage, safePageSize);
    }

    private SubmissionView toView(
            SubmissionEntity entity,
            AssignmentEntity assignment,
            List<SubmissionArtifactView> artifacts,
            boolean revealNonObjectiveScores,
            boolean gradePublished) {
        List<SubmissionAnswerView> answers = submissionAnswerApplicationService.loadAnswerViews(
                entity.getId(), assignment.getId(), revealNonObjectiveScores);
        SubmissionScoreSummaryView scoreSummary = submissionAnswerApplicationService.loadScoreSummary(
                entity.getId(), assignment.getId(), revealNonObjectiveScores, gradePublished);
        return new SubmissionView(
                entity.getId(),
                entity.getSubmissionNo(),
                assignment.getId(),
                assignment.getTitle(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                entity.getSubmitterUserId(),
                entity.getAttemptNo(),
                SubmissionStatus.valueOf(entity.getStatus()),
                entity.getContentText(),
                artifacts,
                answers,
                scoreSummary,
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SubmissionArtifactView toArtifactView(SubmissionArtifactEntity entity) {
        return new SubmissionArtifactView(
                entity.getId(),
                entity.getAssignmentId(),
                entity.getSubmissionId(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getUploadedAt());
    }

    private List<SubmissionArtifactView> toArtifactViews(List<SubmissionArtifactEntity> entities) {
        return entities.stream().map(this::toArtifactView).toList();
    }

    private List<SubmissionArtifactView> loadArtifactViews(Long submissionId) {
        return loadArtifactViewsBySubmissionIds(List.of(submissionId)).getOrDefault(submissionId, List.of());
    }

    private Map<Long, List<SubmissionArtifactView>> loadArtifactViewsBySubmissionIds(Collection<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SubmissionArtifactView>> artifactsBySubmissionId = new LinkedHashMap<>();
        submissionArtifactMapper
                .selectList(Wrappers.<SubmissionArtifactEntity>lambdaQuery()
                        .in(SubmissionArtifactEntity::getSubmissionId, submissionIds)
                        .orderByAsc(SubmissionArtifactEntity::getUploadedAt)
                        .orderByAsc(SubmissionArtifactEntity::getId))
                .forEach(artifact -> artifactsBySubmissionId
                        .computeIfAbsent(artifact.getSubmissionId(), ignored -> new java.util.ArrayList<>())
                        .add(toArtifactView(artifact)));
        return artifactsBySubmissionId;
    }

    private List<SubmissionEntity> loadLatestSubmissions(List<SubmissionEntity> entities) {
        return new LinkedHashMap<Long, SubmissionEntity>() {
            {
                entities.forEach(entity -> putIfAbsent(entity.getSubmitterUserId(), entity));
            }
        }.values().stream().toList();
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "任务不存在");
        }
        return assignment;
    }

    private SubmissionEntity requireSubmission(Long submissionId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在");
        }
        return submission;
    }

    private SubmissionArtifactEntity requireArtifact(Long artifactId) {
        SubmissionArtifactEntity artifact = submissionArtifactMapper.selectById(artifactId);
        if (artifact == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_ARTIFACT_NOT_FOUND", "提交附件不存在");
        }
        return artifact;
    }

    private void assertCanPrepareSubmission(
            AuthenticatedUserPrincipal principal, AssignmentEntity assignment, OffsetDateTime now) {
        assertCanSubmit(principal, assignment);
        validateSubmissionWindow(assignment, now);
    }

    private void assertCanSubmit(AuthenticatedUserPrincipal principal, AssignmentEntity assignment) {
        if (!AssignmentStatus.PUBLISHED.name().equals(assignment.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ASSIGNMENT_UNAVAILABLE", "当前作业暂不允许提交");
        }
        if (!courseAuthorizationService.hasActiveMemberRole(
                principal.getUserId(),
                assignment.getOfferingId(),
                assignment.getTeachingClassId(),
                CourseMemberRole.STUDENT)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权提交该作业");
        }
    }

    private void validateSubmissionWindow(AssignmentEntity assignment, OffsetDateTime now) {
        if (now.isBefore(assignment.getOpenAt()) || now.isAfter(assignment.getDueAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_WINDOW_INVALID", "当前不在作业开放提交时间内");
        }
    }

    private void assertSubmissionAttemptsAvailable(AssignmentEntity assignment, Long userId) {
        if (countAttempts(assignment.getId(), userId) >= assignment.getMaxSubmissions()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_LIMIT_REACHED", "已达到当前作业最大提交次数");
        }
    }

    private int countAttempts(Long assignmentId, Long userId) {
        return submissionMapper
                .selectCount(Wrappers.<SubmissionEntity>lambdaQuery()
                        .eq(SubmissionEntity::getAssignmentId, assignmentId)
                        .eq(SubmissionEntity::getSubmitterUserId, userId))
                .intValue();
    }

    private List<Long> normalizeArtifactIds(List<Long> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        for (Long artifactId : artifactIds) {
            if (artifactId == null || artifactId <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_ID_INVALID", "提交附件标识无效");
            }
            if (!normalizedIds.add(artifactId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_DUPLICATED", "提交附件不能重复引用");
            }
        }
        if (normalizedIds.size() > MAX_ARTIFACTS_PER_SUBMISSION) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_LIMIT_EXCEEDED", "单次提交最多只能关联 10 个附件");
        }
        return normalizedIds.stream().toList();
    }

    private List<Long> extractStructuredArtifactIds(List<SubmissionAnswerInput> answerInputs) {
        if (answerInputs == null || answerInputs.isEmpty()) {
            return List.of();
        }
        return answerInputs.stream()
                .filter(Objects::nonNull)
                .map(SubmissionAnswerInput::artifactIds)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();
    }

    private List<SubmissionArtifactEntity> loadAttachableArtifacts(
            Long assignmentId, Long uploaderUserId, List<Long> artifactIds) {
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionArtifactEntity> artifactIndex = submissionArtifactMapper.selectByIds(artifactIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionArtifactEntity::getId, artifact -> artifact, (left, right) -> left));
        if (artifactIndex.size() != artifactIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_NOT_FOUND", "存在提交附件不存在");
        }
        return artifactIds.stream()
                .map(artifactId -> {
                    SubmissionArtifactEntity artifact = artifactIndex.get(artifactId);
                    if (!Objects.equals(artifact.getAssignmentId(), assignmentId)
                            || !Objects.equals(artifact.getUploaderUserId(), uploaderUserId)) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_SCOPE_INVALID", "提交附件不属于当前任务或当前用户");
                    }
                    if (artifact.getSubmissionId() != null) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_ALREADY_ATTACHED", "提交附件已经关联到其他提交");
                    }
                    return artifact;
                })
                .toList();
    }

    private void attachArtifacts(Long submissionId, List<SubmissionArtifactEntity> artifacts) {
        for (SubmissionArtifactEntity artifact : artifacts) {
            artifact.setSubmissionId(submissionId);
            submissionArtifactMapper.updateById(artifact);
        }
    }

    private String normalizeContentText(String contentText, boolean allowEmpty) {
        if (!StringUtils.hasText(contentText)) {
            if (allowEmpty) {
                return null;
            }
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_PAYLOAD_REQUIRED", "提交内容和附件不能同时为空");
        }
        String normalized = contentText.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_CONTENT_TOO_LONG", "提交内容长度不能超过 20000");
        }
        return normalized;
    }

    private void validateLegacySubmissionEnvelope(List<SubmissionAnswerInput> answerInputs) {
        if (answerInputs != null && !answerInputs.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_LEGACY_PAYLOAD_INVALID", "当前非结构化作业不接受分题答案提交");
        }
    }

    private void validateStructuredSubmissionEnvelope(String contentText, List<Long> artifactIds) {
        if (StringUtils.hasText(contentText) || (artifactIds != null && !artifactIds.isEmpty())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "SUBMISSION_STRUCTURED_PAYLOAD_INVALID",
                    "结构化作业必须通过 answers 提交，不能继续使用 legacy 顶层内容字段");
        }
    }

    private byte[] readArtifactContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_EMPTY", "提交附件不能为空");
        }
        if (file.getSize() > MAX_ARTIFACT_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_TOO_LARGE", "提交附件大小不能超过 20MB");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_EMPTY", "提交附件不能为空");
            }
            return content;
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_READ_FAILED", "无法读取提交附件");
        }
    }

    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_FILENAME_REQUIRED", "提交附件文件名不能为空");
        }
        String normalized = StringUtils.cleanPath(originalFilename).replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_FILENAME_REQUIRED", "提交附件文件名不能为空");
        }
        if (normalized.length() > 255) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_FILENAME_TOO_LONG", "提交附件文件名长度不能超过 255");
        }
        return normalized;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String generateSubmissionNo() {
        return "SUB-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateArtifactObjectKey(Long assignmentId, Long userId, String originalFilename) {
        String safeFilename = originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(safeFilename)) {
            safeFilename = "artifact.bin";
        }
        if (safeFilename.length() > MAX_STORAGE_FILENAME_LENGTH) {
            safeFilename = safeFilename.substring(safeFilename.length() - MAX_STORAGE_FILENAME_LENGTH);
        }
        return "%s/%s/%s/%s/%s"
                .formatted(
                        OBJECT_KEY_PREFIX,
                        assignmentId,
                        userId,
                        UUID.randomUUID().toString().replace("-", ""),
                        safeFilename);
    }

    private ObjectStorageService requireStorageService() {
        ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "SUBMISSION_STORAGE_UNAVAILABLE", "当前环境未启用提交对象存储");
        }
        return storageService;
    }

    private SubmissionArtifactDownload loadStoredArtifact(SubmissionArtifactEntity artifact) {
        try {
            StoredObject storedObject = requireStorageService().getObject(artifact.getObjectKey());
            return new SubmissionArtifactDownload(
                    artifact.getOriginalFilename(), storedObject.contentType(), storedObject.content());
        } catch (ObjectStorageException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "SUBMISSION_STORAGE_READ_FAILED", "提交附件暂时无法读取");
        }
    }

    private void safeDeleteObject(String objectKey) {
        try {
            ObjectStorageService storageService = objectStorageServiceProvider.getIfAvailable();
            if (storageService != null && storageService.objectExists(objectKey)) {
                storageService.deleteObject(objectKey);
            }
        } catch (RuntimeException ignored) {
            // 回滚路径只做尽力清理，避免对象存储错误覆盖主事务异常。
        }
    }
}
