package com.aubb.server.modules.submission.application.workspace;

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
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.application.SubmissionArtifactView;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactMapper;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceEntity;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
public class ProgrammingWorkspaceApplicationService {

    private static final int MAX_CODE_TEXT_LENGTH = 50_000;
    private static final int MAX_ARTIFACT_COUNT = 10;

    private final ProgrammingWorkspaceMapper programmingWorkspaceMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final SubmissionArtifactMapper submissionArtifactMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProgrammingWorkspaceView getMyWorkspace(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context = requireVisibleProgrammingQuestion(assignmentId, questionId, principal);
        ProgrammingWorkspaceEntity entity = loadWorkspace(questionId, principal.getUserId());
        return toView(context.assignment(), context.question(), entity);
    }

    @Transactional
    public ProgrammingWorkspaceView saveMyWorkspace(
            Long assignmentId,
            Long questionId,
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context = requireVisibleProgrammingQuestion(assignmentId, questionId, principal);
        List<Long> normalizedArtifactIds = normalizeArtifactIds(artifactIds);
        List<SubmissionArtifactEntity> artifacts =
                loadScopedArtifacts(assignmentId, principal.getUserId(), normalizedArtifactIds);
        ProgrammingLanguage resolvedLanguage =
                resolveLanguage(programmingLanguage, context.question().config());
        validateProgrammingInput(context.question().config(), artifacts, resolvedLanguage);
        String normalizedCodeText = normalizeCodeText(codeText);

        ProgrammingWorkspaceEntity entity = loadWorkspace(questionId, principal.getUserId());
        if (entity == null) {
            entity = new ProgrammingWorkspaceEntity();
            entity.setAssignmentId(assignmentId);
            entity.setAssignmentQuestionId(questionId);
            entity.setUserId(principal.getUserId());
        }
        entity.setProgrammingLanguage(resolvedLanguage.name());
        entity.setCodeText(normalizedCodeText);
        entity.setArtifactIdsJson(writeArtifactIds(normalizedArtifactIds));
        if (entity.getId() == null) {
            programmingWorkspaceMapper.insert(entity);
        } else {
            programmingWorkspaceMapper.updateById(entity);
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.PROGRAMMING_WORKSPACE_SAVED,
                "PROGRAMMING_WORKSPACE",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "assignmentId",
                        assignmentId,
                        "assignmentQuestionId",
                        questionId,
                        "artifactCount",
                        normalizedArtifactIds.size(),
                        "programmingLanguage",
                        resolvedLanguage.name()));
        return toView(context.assignment(), context.question(), entity);
    }

    private ProgrammingWorkspaceEntity loadWorkspace(Long questionId, Long userId) {
        return programmingWorkspaceMapper.selectOne(Wrappers.<ProgrammingWorkspaceEntity>lambdaQuery()
                .eq(ProgrammingWorkspaceEntity::getAssignmentQuestionId, questionId)
                .eq(ProgrammingWorkspaceEntity::getUserId, userId)
                .last("LIMIT 1"));
    }

    private ProgrammingWorkspaceView toView(
            AssignmentEntity assignment, AssignmentQuestionSnapshot question, ProgrammingWorkspaceEntity entity) {
        AssignmentQuestionConfigInput config = question.config();
        List<Long> artifactIds = entity == null ? List.of() : readArtifactIds(entity.getArtifactIdsJson());
        List<SubmissionArtifactView> artifacts = loadArtifactViews(artifactIds);
        ProgrammingLanguage programmingLanguage =
                entity == null ? defaultLanguage(config) : ProgrammingLanguage.valueOf(entity.getProgrammingLanguage());
        return new ProgrammingWorkspaceView(
                assignment.getId(),
                question.id(),
                programmingLanguage,
                entity == null ? null : entity.getCodeText(),
                artifactIds,
                artifacts,
                entity == null ? null : entity.getUpdatedAt());
    }

    private List<SubmissionArtifactView> loadArtifactViews(List<Long> artifactIds) {
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionArtifactEntity> artifactIndex = submissionArtifactMapper.selectByIds(artifactIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SubmissionArtifactEntity::getId,
                        artifact -> artifact,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return artifactIds.stream()
                .map(artifactIndex::get)
                .filter(Objects::nonNull)
                .map(artifact -> new SubmissionArtifactView(
                        artifact.getId(),
                        artifact.getAssignmentId(),
                        artifact.getSubmissionId(),
                        artifact.getOriginalFilename(),
                        artifact.getContentType(),
                        artifact.getSizeBytes(),
                        artifact.getUploadedAt()))
                .toList();
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
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权访问该编程题工作区");
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_NOT_FOUND", "存在工作区附件不存在");
        }
        return artifactIds.stream()
                .map(artifactId -> {
                    SubmissionArtifactEntity artifact = artifactIndex.get(artifactId);
                    if (!Objects.equals(artifact.getAssignmentId(), assignmentId)
                            || !Objects.equals(artifact.getUploaderUserId(), userId)) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_SCOPE_INVALID", "工作区附件不属于当前任务或当前用户");
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
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_ID_INVALID", "工作区附件标识无效");
            }
            if (!normalizedIds.add(artifactId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_DUPLICATED", "工作区附件不能重复引用");
            }
        }
        if (normalizedIds.size() > MAX_ARTIFACT_COUNT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_ARTIFACT_LIMIT_EXCEEDED", "工作区最多只能关联 10 个附件");
        }
        return normalizedIds.stream().toList();
    }

    private ProgrammingLanguage resolveLanguage(
            ProgrammingLanguage programmingLanguage, AssignmentQuestionConfigInput config) {
        if (programmingLanguage != null) {
            return programmingLanguage;
        }
        ProgrammingLanguage defaultLanguage = defaultLanguage(config);
        if (defaultLanguage == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_REQUIRED", "编程题工作区必须指定编程语言");
        }
        return defaultLanguage;
    }

    private ProgrammingLanguage defaultLanguage(AssignmentQuestionConfigInput config) {
        return config == null
                        || config.supportedLanguages() == null
                        || config.supportedLanguages().isEmpty()
                ? null
                : config.supportedLanguages().getFirst();
    }

    private void validateProgrammingInput(
            AssignmentQuestionConfigInput config,
            List<SubmissionArtifactEntity> artifacts,
            ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_REQUIRED", "编程题工作区必须指定编程语言");
        }
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_CODE_TOO_LONG", "编程题工作区代码正文长度超过限制");
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
            throw new IllegalStateException("工作区附件列表无法序列化", exception);
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
            throw new IllegalStateException("工作区附件列表无法读取", exception);
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
