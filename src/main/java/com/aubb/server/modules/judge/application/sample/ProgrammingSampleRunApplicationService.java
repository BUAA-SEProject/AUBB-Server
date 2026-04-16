package com.aubb.server.modules.judge.application.sample;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.JudgeExecutionService;
import com.aubb.server.modules.judge.application.JudgeExecutionService.ProgrammingSampleRunOutcome;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.aubb.server.modules.judge.domain.ProgrammingSampleRunStatus;
import com.aubb.server.modules.judge.infrastructure.sample.ProgrammingSampleRunEntity;
import com.aubb.server.modules.judge.infrastructure.sample.ProgrammingSampleRunMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProgrammingSampleRunApplicationService {

    private static final int MAX_CODE_TEXT_LENGTH = 50_000;
    private static final int MAX_ARTIFACT_COUNT = 10;

    private final ProgrammingSampleRunMapper programmingSampleRunMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final SubmissionArtifactMapper submissionArtifactMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final JudgeExecutionService judgeExecutionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProgrammingSampleRunView createMySampleRun(
            Long assignmentId,
            Long questionId,
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            AuthenticatedUserPrincipal principal) {
        OffsetDateTime now = OffsetDateTime.now();
        ProgrammingQuestionContext context =
                requireRunnableProgrammingQuestion(assignmentId, questionId, principal, now);
        List<Long> normalizedArtifactIds = normalizeArtifactIds(artifactIds);
        List<SubmissionArtifactEntity> artifacts =
                loadScopedArtifacts(assignmentId, principal.getUserId(), normalizedArtifactIds);
        ProgrammingLanguage resolvedLanguage =
                resolveLanguage(programmingLanguage, context.question().config());
        validateProgrammingInput(context.question().config(), artifacts, resolvedLanguage);
        String normalizedCodeText = normalizeCodeText(codeText);

        ProgrammingSampleRunEntity entity = new ProgrammingSampleRunEntity();
        entity.setAssignmentId(assignmentId);
        entity.setAssignmentQuestionId(questionId);
        entity.setUserId(principal.getUserId());
        entity.setProgrammingLanguage(resolvedLanguage.name());
        entity.setCodeText(normalizedCodeText);
        entity.setArtifactIdsJson(writeArtifactIds(normalizedArtifactIds));
        entity.setStdinText(context.question().config().sampleStdinText());
        entity.setExpectedStdout(context.question().config().sampleExpectedStdout());
        entity.setStatus(ProgrammingSampleRunStatus.RUNNING.name());
        entity.setStartedAt(now);
        programmingSampleRunMapper.insert(entity);

        ProgrammingSampleRunOutcome outcome = judgeExecutionService.runProgrammingSample(
                context.question(), normalizedCodeText, normalizedArtifactIds, resolvedLanguage);
        entity.setStatus(
                outcome.failed()
                        ? ProgrammingSampleRunStatus.FAILED.name()
                        : ProgrammingSampleRunStatus.SUCCEEDED.name());
        entity.setVerdict(outcome.verdict() == null ? null : outcome.verdict().name());
        entity.setStdoutText(outcome.stdoutText());
        entity.setStderrText(outcome.stderrText());
        entity.setResultSummary(outcome.resultSummary());
        entity.setErrorMessage(outcome.errorMessage());
        entity.setTimeMillis(outcome.timeMillis());
        entity.setMemoryBytes(outcome.memoryBytes());
        entity.setFinishedAt(OffsetDateTime.now());
        programmingSampleRunMapper.updateById(entity);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignmentId);
        metadata.put("assignmentQuestionId", questionId);
        metadata.put("programmingLanguage", resolvedLanguage.name());
        metadata.put("artifactCount", normalizedArtifactIds.size());
        metadata.put("status", entity.getStatus());
        metadata.put("verdict", entity.getVerdict());
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.PROGRAMMING_SAMPLE_RUN_CREATED,
                "PROGRAMMING_SAMPLE_RUN",
                String.valueOf(entity.getId()),
                outcome.failed() ? AuditResult.FAILURE : AuditResult.SUCCESS,
                metadata);
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public List<ProgrammingSampleRunView> listMySampleRuns(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal) {
        requireVisibleProgrammingQuestion(assignmentId, questionId, principal);
        return programmingSampleRunMapper
                .selectList(Wrappers.<ProgrammingSampleRunEntity>lambdaQuery()
                        .eq(ProgrammingSampleRunEntity::getAssignmentId, assignmentId)
                        .eq(ProgrammingSampleRunEntity::getAssignmentQuestionId, questionId)
                        .eq(ProgrammingSampleRunEntity::getUserId, principal.getUserId())
                        .orderByDesc(ProgrammingSampleRunEntity::getCreatedAt)
                        .orderByDesc(ProgrammingSampleRunEntity::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    private ProgrammingSampleRunView toView(ProgrammingSampleRunEntity entity) {
        return new ProgrammingSampleRunView(
                entity.getId(),
                entity.getAssignmentId(),
                entity.getAssignmentQuestionId(),
                ProgrammingLanguage.valueOf(entity.getProgrammingLanguage()),
                readArtifactIds(entity.getArtifactIdsJson()),
                ProgrammingSampleRunStatus.valueOf(entity.getStatus()),
                entity.getVerdict() == null ? null : JudgeVerdict.valueOf(entity.getVerdict()),
                entity.getStdinText(),
                entity.getExpectedStdout(),
                entity.getStdoutText(),
                entity.getStderrText(),
                entity.getResultSummary(),
                entity.getErrorMessage(),
                entity.getTimeMillis(),
                entity.getMemoryBytes(),
                entity.getCreatedAt(),
                entity.getFinishedAt());
    }

    private ProgrammingQuestionContext requireRunnableProgrammingQuestion(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal, OffsetDateTime now) {
        ProgrammingQuestionContext context = requireVisibleProgrammingQuestion(assignmentId, questionId, principal);
        AssignmentEntity assignment = context.assignment();
        if (!AssignmentStatus.PUBLISHED.name().equals(assignment.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ASSIGNMENT_UNAVAILABLE", "当前作业暂不允许试运行");
        }
        if (!courseAuthorizationService.hasActiveMemberRole(
                principal.getUserId(),
                assignment.getOfferingId(),
                assignment.getTeachingClassId(),
                CourseMemberRole.STUDENT)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权执行该编程题样例试运行");
        }
        if (now.isBefore(assignment.getOpenAt()) || now.isAfter(assignment.getDueAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_WINDOW_INVALID", "当前不在作业开放提交时间内");
        }
        AssignmentQuestionConfigInput config = context.question().config();
        if (!Boolean.TRUE.equals(config.allowSampleRun()) || !StringUtils.hasText(config.sampleStdinText())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SAMPLE_RUN_DISABLED", "当前编程题未启用样例试运行");
        }
        return context;
    }

    private ProgrammingQuestionContext requireVisibleProgrammingQuestion(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "作业不存在");
        }
        if (AssignmentStatus.DRAFT.name().equals(assignment.getStatus())
                || !courseAuthorizationService.canViewAssignment(
                        principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权访问该编程题试运行");
        }
        AssignmentQuestionSnapshot question =
                assignmentPaperApplicationService.loadQuestionSnapshots(assignmentId).stream()
                        .filter(candidate -> Objects.equals(candidate.id(), questionId))
                        .findFirst()
                        .orElseThrow(() ->
                                new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_QUESTION_NOT_FOUND", "题目不存在"));
        if (!AssignmentQuestionType.PROGRAMMING.equals(question.questionType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_QUESTION_REQUIRED", "当前题目不是编程题");
        }
        if (question.config() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_CONFIG_MISSING", "当前编程题缺少运行配置");
        }
        return new ProgrammingQuestionContext(assignment, question);
    }

    private List<SubmissionArtifactEntity> loadScopedArtifacts(Long assignmentId, Long userId, List<Long> artifactIds) {
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionArtifactEntity> artifactIndex = submissionArtifactMapper.selectByIds(artifactIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionArtifactEntity::getId,
                        artifact -> artifact,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (artifactIndex.size() != artifactIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_NOT_FOUND", "存在试运行附件不存在");
        }
        return artifactIds.stream()
                .map(artifactId -> {
                    SubmissionArtifactEntity artifact = artifactIndex.get(artifactId);
                    if (!Objects.equals(artifact.getAssignmentId(), assignmentId)
                            || !Objects.equals(artifact.getUploaderUserId(), userId)) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_SCOPE_INVALID", "试运行附件不属于当前任务或当前用户");
                    }
                    return artifact;
                })
                .toList();
    }

    private List<Long> normalizeArtifactIds(List<Long> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        for (Long artifactId : artifactIds) {
            if (artifactId == null || artifactId <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_ID_INVALID", "试运行附件标识无效");
            }
            if (!normalizedIds.add(artifactId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_DUPLICATED", "试运行附件不能重复引用");
            }
        }
        if (normalizedIds.size() > MAX_ARTIFACT_COUNT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_LIMIT_EXCEEDED", "样例试运行最多只能关联 10 个附件");
        }
        return normalizedIds.stream().toList();
    }

    private ProgrammingLanguage resolveLanguage(
            ProgrammingLanguage programmingLanguage, AssignmentQuestionConfigInput config) {
        if (programmingLanguage != null) {
            return programmingLanguage;
        }
        if (config.supportedLanguages() == null || config.supportedLanguages().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_REQUIRED", "样例试运行必须指定编程语言");
        }
        return config.supportedLanguages().getFirst();
    }

    private void validateProgrammingInput(
            AssignmentQuestionConfigInput config,
            List<SubmissionArtifactEntity> artifacts,
            ProgrammingLanguage programmingLanguage) {
        if (config.supportedLanguages() != null && !config.supportedLanguages().contains(programmingLanguage)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_UNSUPPORTED", "所选语言不在题目支持范围内");
        }
        if (config.maxFileCount() != null && artifacts.size() > config.maxFileCount()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_LIMIT_EXCEEDED", "上传文件数量超过题目限制");
        }
        if (config.maxFileSizeMb() != null) {
            long maxFileSizeBytes = config.maxFileSizeMb() * 1024L * 1024L;
            for (SubmissionArtifactEntity artifact : artifacts) {
                if (artifact.getSizeBytes() != null && artifact.getSizeBytes() > maxFileSizeBytes) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_SIZE_EXCEEDED", "存在文件大小超过题目限制");
                }
            }
        }
        if (!Boolean.TRUE.equals(config.allowMultipleFiles()) && artifacts.size() > 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_MULTIPLE_FILES_DISABLED", "当前题目不允许上传多个文件");
        }
        if (config.acceptedExtensions() != null && !config.acceptedExtensions().isEmpty()) {
            LinkedHashSet<String> acceptedExtensions = normalizeExtensions(config.acceptedExtensions());
            for (SubmissionArtifactEntity artifact : artifacts) {
                String extension = extensionOf(artifact.getOriginalFilename());
                if (!acceptedExtensions.contains(extension)) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_EXTENSION_INVALID", "存在文件扩展名不符合题目限制");
                }
            }
        }
    }

    private String normalizeCodeText(String codeText) {
        if (!StringUtils.hasText(codeText)) {
            return null;
        }
        String normalized = codeText.stripTrailing();
        if (normalized.length() > MAX_CODE_TEXT_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_CODE_TOO_LONG", "样例试运行代码正文长度超过限制");
        }
        return normalized;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String writeArtifactIds(List<Long> artifactIds) {
        try {
            return objectMapper.writeValueAsString(artifactIds == null ? List.of() : artifactIds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行附件列表无法序列化", exception);
        }
    }

    private List<Long> readArtifactIds(String artifactIdsJson) {
        if (!StringUtils.hasText(artifactIdsJson)) {
            return List.of();
        }
        try {
            Long[] artifactIds = objectMapper.readValue(artifactIdsJson, Long[].class);
            return artifactIds == null ? List.of() : Arrays.stream(artifactIds).toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行附件列表无法读取", exception);
        }
    }

    private LinkedHashSet<String> normalizeExtensions(List<String> extensions) {
        return extensions.stream()
                .filter(StringUtils::hasText)
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private record ProgrammingQuestionContext(AssignmentEntity assignment, AssignmentQuestionSnapshot question) {}
}
