package com.aubb.server.modules.judge.application.sample;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.common.programming.ProgrammingSourceSnapshot;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigInput;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.ReadPathAuthorizationService;
import com.aubb.server.modules.judge.application.JudgeArtifactStorageService;
import com.aubb.server.modules.judge.application.JudgeExecutionService;
import com.aubb.server.modules.judge.application.JudgeExecutionService.ProgrammingSampleRunOutcome;
import com.aubb.server.modules.judge.application.JudgeJobStoredReport;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.aubb.server.modules.judge.domain.ProgrammingSampleRunInputMode;
import com.aubb.server.modules.judge.domain.ProgrammingSampleRunStatus;
import com.aubb.server.modules.judge.infrastructure.sample.ProgrammingSampleRunEntity;
import com.aubb.server.modules.judge.infrastructure.sample.ProgrammingSampleRunMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactMapper;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceEntity;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceMapper;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceRevisionEntity;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceRevisionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgrammingSampleRunApplicationService {

    private static final int MAX_CODE_TEXT_LENGTH = 50_000;
    private static final int MAX_ARTIFACT_COUNT = 10;
    private static final int MAX_SOURCE_FILE_COUNT = 20;
    private static final int MAX_DIRECTORY_COUNT = 40;
    private static final int MAX_SOURCE_FILE_PATH_LENGTH = 200;
    private static final Pattern SAFE_SOURCE_FILE_PATH = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final ProgrammingSampleRunMapper programmingSampleRunMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final SubmissionArtifactMapper submissionArtifactMapper;
    private final ProgrammingWorkspaceMapper programmingWorkspaceMapper;
    private final ProgrammingWorkspaceRevisionMapper programmingWorkspaceRevisionMapper;
    private final ReadPathAuthorizationService readPathAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final JudgeExecutionService judgeExecutionService;
    private final JudgeArtifactStorageService judgeArtifactStorageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProgrammingSampleRunView createMySampleRun(
            Long assignmentId,
            Long questionId,
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files,
            List<String> directories,
            String stdinText,
            String expectedStdout,
            Boolean useWorkspaceSnapshot,
            Long workspaceRevisionId,
            AuthenticatedUserPrincipal principal) {
        OffsetDateTime now = OffsetDateTime.now();
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.run");

        ProgrammingSampleRunInputMode inputMode =
                resolveInputMode(stdinText, expectedStdout, context.question().config());
        SampleRunSource source = resolveSampleRunSource(
                assignmentId,
                questionId,
                context.question().config(),
                principal.getUserId(),
                programmingLanguage,
                codeText,
                artifactIds,
                entryFilePath,
                files,
                directories,
                useWorkspaceSnapshot,
                workspaceRevisionId);
        List<SubmissionArtifactEntity> artifacts =
                loadScopedArtifacts(assignmentId, principal.getUserId(), source.artifactIds());
        validateProgrammingInput(
                context.question().config(),
                artifacts,
                source.programmingLanguage(),
                source.sourceSnapshot(),
                source.directories());
        ExecutionInput executionInput =
                resolveExecutionInput(context.question().config(), inputMode, stdinText, expectedStdout, source);

        ProgrammingSampleRunEntity entity = new ProgrammingSampleRunEntity();
        entity.setAssignmentId(assignmentId);
        entity.setAssignmentQuestionId(questionId);
        entity.setUserId(principal.getUserId());
        entity.setProgrammingLanguage(source.programmingLanguage().name());
        entity.setArtifactIdsJson(writeArtifactIds(source.artifactIds()));
        entity.setEntryFilePath(source.sourceSnapshot().entryFilePath());
        entity.setWorkspaceRevisionId(source.workspaceRevisionId());
        entity.setInputMode(inputMode.name());
        entity.setStdinText(executionInput.stdinText());
        entity.setExpectedStdout(executionInput.expectedStdout());
        entity.setStatus(ProgrammingSampleRunStatus.RUNNING.name());
        entity.setStartedAt(now);
        entity.setCodeText(null);
        entity.setSourceFilesJson(writeSourceFiles(List.of()));
        entity.setSourceDirectoriesJson(writeDirectories(List.of()));
        programmingSampleRunMapper.insert(entity);

        String sourceSnapshotObjectKey = judgeArtifactStorageService.storeProgrammingSampleRunSourceSnapshot(
                entity.getId(),
                new ProgrammingSampleRunStoredSource(
                        source.programmingLanguage(),
                        source.sourceSnapshot().entryFilePath(),
                        source.sourceSnapshot().files(),
                        source.directories(),
                        source.artifactIds(),
                        source.workspaceRevisionId()));
        entity.setSourceSnapshotObjectKey(sourceSnapshotObjectKey);
        if (!StringUtils.hasText(sourceSnapshotObjectKey)) {
            entity.setCodeText(source.sourceSnapshot().entryCodeText());
            entity.setSourceFilesJson(writeSourceFiles(source.sourceSnapshot().files()));
            entity.setSourceDirectoriesJson(writeDirectories(source.directories()));
        }

        ProgrammingSampleRunOutcome outcome = judgeExecutionService.runProgrammingSample(
                context.question(),
                source.sourceSnapshot(),
                source.artifactIds(),
                source.programmingLanguage(),
                executionInput.stdinText(),
                executionInput.expectedStdout(),
                inputMode,
                source.workspaceRevisionId());
        entity.setStatus(
                outcome.failed()
                        ? ProgrammingSampleRunStatus.FAILED.name()
                        : ProgrammingSampleRunStatus.SUCCEEDED.name());
        entity.setVerdict(outcome.verdict() == null ? null : outcome.verdict().name());
        entity.setResultSummary(outcome.resultSummary());
        entity.setErrorMessage(outcome.errorMessage());
        entity.setTimeMillis(outcome.timeMillis());
        entity.setMemoryBytes(outcome.memoryBytes());
        String detailReportObjectKey = judgeArtifactStorageService.storeProgrammingSampleRunDetailReport(
                entity.getId(), outcome.detailReport());
        entity.setDetailReportObjectKey(detailReportObjectKey);
        entity.setStdoutText(outcome.stdoutText());
        entity.setStderrText(outcome.stderrText());
        if (StringUtils.hasText(detailReportObjectKey)) {
            entity.setDetailReportJson(null);
        } else {
            entity.setDetailReportJson(writeDetailReport(outcome.detailReport()));
        }
        entity.setFinishedAt(OffsetDateTime.now());
        programmingSampleRunMapper.updateById(entity);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignmentId);
        metadata.put("assignmentQuestionId", questionId);
        metadata.put("programmingLanguage", source.programmingLanguage().name());
        metadata.put("artifactCount", source.artifactIds().size());
        metadata.put("sourceFileCount", source.sourceSnapshot().files().size());
        metadata.put("directoryCount", source.directories().size());
        metadata.put("entryFilePath", source.sourceSnapshot().entryFilePath());
        metadata.put("status", entity.getStatus());
        metadata.put("verdict", entity.getVerdict());
        metadata.put("inputMode", inputMode.name());
        metadata.put("workspaceRevisionId", source.workspaceRevisionId());
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.PROGRAMMING_SAMPLE_RUN_CREATED,
                "PROGRAMMING_SAMPLE_RUN",
                String.valueOf(entity.getId()),
                outcome.failed() ? AuditResult.FAILURE : AuditResult.SUCCESS,
                metadata);
        return toView(entity, true);
    }

    @Transactional(readOnly = true)
    public List<ProgrammingSampleRunView> listMySampleRuns(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal) {
        requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.read");
        return programmingSampleRunMapper
                .selectList(Wrappers.<ProgrammingSampleRunEntity>lambdaQuery()
                        .eq(ProgrammingSampleRunEntity::getAssignmentId, assignmentId)
                        .eq(ProgrammingSampleRunEntity::getAssignmentQuestionId, questionId)
                        .eq(ProgrammingSampleRunEntity::getUserId, principal.getUserId())
                        .orderByDesc(ProgrammingSampleRunEntity::getCreatedAt)
                        .orderByDesc(ProgrammingSampleRunEntity::getId))
                .stream()
                .map(entity -> toView(entity, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgrammingSampleRunView getMySampleRun(
            Long assignmentId, Long questionId, Long sampleRunId, AuthenticatedUserPrincipal principal) {
        requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.read");
        ProgrammingSampleRunEntity entity = programmingSampleRunMapper.selectById(sampleRunId);
        if (entity == null
                || !Objects.equals(entity.getAssignmentId(), assignmentId)
                || !Objects.equals(entity.getAssignmentQuestionId(), questionId)
                || !Objects.equals(entity.getUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "PROGRAMMING_SAMPLE_RUN_NOT_FOUND", "样例试运行记录不存在");
        }
        return toView(entity, true);
    }

    private ProgrammingSampleRunView toView(ProgrammingSampleRunEntity entity, boolean includeDetailReport) {
        ProgrammingSampleRunStoredSource storedSource =
                judgeArtifactStorageService.loadProgrammingSampleRunSourceSnapshot(entity);
        ProgrammingLanguage programmingLanguage = storedSource == null
                ? ProgrammingLanguage.valueOf(entity.getProgrammingLanguage())
                : storedSource.programmingLanguage();
        ProgrammingSourceSnapshot sourceSnapshot = storedSource == null
                ? ProgrammingSourceSnapshot.fromInput(
                        programmingLanguage,
                        entity.getCodeText(),
                        entity.getEntryFilePath(),
                        readSourceFiles(entity.getSourceFilesJson()))
                : ProgrammingSourceSnapshot.fromInput(
                        programmingLanguage, null, storedSource.entryFilePath(), storedSource.files());
        DetailReportLoadResult detailReportResult = shouldLoadDetailReport(entity, includeDetailReport)
                ? readDetailReport(entity)
                : new DetailReportLoadResult(null, null);
        JudgeJobStoredReport detailReport = detailReportResult.detailReport();
        String stdoutText = detailReport == null ? entity.getStdoutText() : detailReport.stdoutText();
        String stderrText =
                StringUtils.hasText(detailReport == null ? entity.getStderrText() : detailReport.stderrText())
                        ? detailReport == null ? entity.getStderrText() : detailReport.stderrText()
                        : "";
        return new ProgrammingSampleRunView(
                entity.getId(),
                entity.getAssignmentId(),
                entity.getAssignmentQuestionId(),
                programmingLanguage,
                sourceSnapshot.entryFilePath(),
                sourceSnapshot.files(),
                storedSource == null
                        ? normalizeDirectories(
                                readDirectories(entity.getSourceDirectoriesJson()), sourceSnapshot.files(), true)
                        : normalizeDirectories(storedSource.directories(), sourceSnapshot.files(), true),
                storedSource == null ? readArtifactIds(entity.getArtifactIdsJson()) : storedSource.artifactIds(),
                storedSource == null ? entity.getWorkspaceRevisionId() : storedSource.workspaceRevisionId(),
                ProgrammingSampleRunInputMode.valueOf(entity.getInputMode()),
                ProgrammingSampleRunStatus.valueOf(entity.getStatus()),
                entity.getVerdict() == null ? null : JudgeVerdict.valueOf(entity.getVerdict()),
                entity.getStdinText(),
                entity.getExpectedStdout(),
                stdoutText,
                stderrText,
                entity.getResultSummary(),
                entity.getErrorMessage(),
                entity.getTimeMillis(),
                entity.getMemoryBytes(),
                includeDetailReport ? detailReport : null,
                includeDetailReport ? detailReportResult.unavailableReasonCode() : null,
                entity.getCreatedAt(),
                entity.getFinishedAt());
    }

    private boolean shouldLoadDetailReport(ProgrammingSampleRunEntity entity, boolean includeDetailReport) {
        return includeDetailReport
                && (StringUtils.hasText(entity.getDetailReportObjectKey())
                        || StringUtils.hasText(entity.getDetailReportJson()));
    }

    private ProgrammingQuestionContext requireVisibleProgrammingQuestion(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal, String permissionCode) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "作业不存在");
        }
        if (!readPathAuthorizationService.canAccessMyAssignmentCapability(principal, "task.read", assignment)
                || !readPathAuthorizationService.canAccessMyAssignmentCapability(
                        principal, permissionCode, assignment)) {
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

    private ProgrammingSampleRunInputMode resolveInputMode(
            String stdinText, String expectedStdout, AssignmentQuestionConfigInput config) {
        boolean customRequested = StringUtils.hasText(stdinText) || StringUtils.hasText(expectedStdout);
        if (customRequested) {
            return ProgrammingSampleRunInputMode.CUSTOM;
        }
        if (!Boolean.TRUE.equals(config.allowSampleRun()) || !StringUtils.hasText(config.sampleStdinText())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SAMPLE_RUN_DISABLED", "当前编程题未启用样例试运行");
        }
        return ProgrammingSampleRunInputMode.SAMPLE;
    }

    private ExecutionInput resolveExecutionInput(
            AssignmentQuestionConfigInput config,
            ProgrammingSampleRunInputMode inputMode,
            String stdinText,
            String expectedStdout,
            SampleRunSource source) {
        if (inputMode == ProgrammingSampleRunInputMode.SAMPLE) {
            return new ExecutionInput(
                    normalizeText(config.sampleStdinText()), normalizeText(config.sampleExpectedStdout()));
        }
        String normalizedStdin = normalizeText(stdinText);
        if (!StringUtils.hasText(normalizedStdin)) {
            normalizedStdin = source.lastStdinText();
        }
        if (!StringUtils.hasText(normalizedStdin)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SAMPLE_STDIN_REQUIRED", "试运行必须提供标准输入");
        }
        return new ExecutionInput(normalizedStdin, normalizeText(expectedStdout));
    }

    private SampleRunSource resolveSampleRunSource(
            Long assignmentId,
            Long questionId,
            AssignmentQuestionConfigInput config,
            Long userId,
            ProgrammingLanguage programmingLanguage,
            String codeText,
            List<Long> artifactIds,
            String entryFilePath,
            List<ProgrammingSourceFile> files,
            List<String> directories,
            Boolean useWorkspaceSnapshot,
            Long workspaceRevisionId) {
        if (workspaceRevisionId != null) {
            ProgrammingWorkspaceRevisionEntity revision =
                    programmingWorkspaceRevisionMapper.selectById(workspaceRevisionId);
            if (revision == null
                    || !Objects.equals(revision.getAssignmentId(), assignmentId)
                    || !Objects.equals(revision.getUserId(), userId)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_REVISION_NOT_FOUND", "指定的工作区历史版本不存在");
            }
            ProgrammingLanguage revisionLanguage = ProgrammingLanguage.valueOf(revision.getProgrammingLanguage());
            return new SampleRunSource(
                    revisionLanguage,
                    ProgrammingSourceSnapshot.fromInput(
                            revisionLanguage,
                            revision.getCodeText(),
                            revision.getEntryFilePath(),
                            readSourceFiles(revision.getSourceFilesJson())),
                    readDirectories(revision.getSourceDirectoriesJson()),
                    readArtifactIds(revision.getArtifactIdsJson()),
                    revision.getLastStdinText(),
                    revision.getId());
        }

        boolean hasExplicitSnapshot =
                hasExplicitSnapshot(codeText, artifactIds, programmingLanguage, entryFilePath, files, directories);
        if (hasExplicitSnapshot) {
            List<Long> normalizedArtifactIds = normalizeArtifactIds(artifactIds);
            ProgrammingLanguage resolvedLanguage = resolveLanguage(programmingLanguage, config, null);
            ProgrammingSourceSnapshot sourceSnapshot = ProgrammingSourceSnapshot.fromInput(
                    resolvedLanguage, normalizeCodeText(codeText), entryFilePath, files);
            return new SampleRunSource(
                    resolvedLanguage,
                    sourceSnapshot,
                    normalizeDirectories(directories, sourceSnapshot.files(), true),
                    normalizedArtifactIds,
                    null,
                    null);
        }

        ProgrammingWorkspaceEntity workspace =
                programmingWorkspaceMapper.selectOne(Wrappers.<ProgrammingWorkspaceEntity>lambdaQuery()
                        .eq(ProgrammingWorkspaceEntity::getAssignmentId, assignmentId)
                        .eq(ProgrammingWorkspaceEntity::getAssignmentQuestionId, questionId)
                        .eq(ProgrammingWorkspaceEntity::getUserId, userId)
                        .last("LIMIT 1"));
        if (workspace != null) {
            ProgrammingLanguage workspaceLanguage = ProgrammingLanguage.valueOf(workspace.getProgrammingLanguage());
            ProgrammingWorkspaceRevisionEntity latestRevision = loadLatestRevision(workspace.getId());
            ProgrammingSourceSnapshot sourceSnapshot = ProgrammingSourceSnapshot.fromInput(
                    workspaceLanguage,
                    workspace.getCodeText(),
                    workspace.getEntryFilePath(),
                    readSourceFiles(workspace.getSourceFilesJson()));
            return new SampleRunSource(
                    workspaceLanguage,
                    sourceSnapshot,
                    normalizeDirectories(
                            readDirectories(workspace.getSourceDirectoriesJson()), sourceSnapshot.files(), true),
                    readArtifactIds(workspace.getArtifactIdsJson()),
                    workspace.getLastStdinText(),
                    latestRevision == null ? null : latestRevision.getId());
        }

        if (Boolean.TRUE.equals(useWorkspaceSnapshot)) {
            SampleRunSource templateSource = buildTemplateSource(config, programmingLanguage);
            if (templateSource.sourceSnapshot().files().isEmpty()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_EMPTY", "当前工作区尚未保存，且题目未配置模板工程");
            }
            return templateSource;
        }
        return buildTemplateSource(config, programmingLanguage);
    }

    private ProgrammingWorkspaceRevisionEntity loadLatestRevision(Long workspaceId) {
        return programmingWorkspaceRevisionMapper.selectOne(Wrappers.<ProgrammingWorkspaceRevisionEntity>lambdaQuery()
                .eq(ProgrammingWorkspaceRevisionEntity::getWorkspaceId, workspaceId)
                .orderByDesc(ProgrammingWorkspaceRevisionEntity::getRevisionNo)
                .orderByDesc(ProgrammingWorkspaceRevisionEntity::getId)
                .last("LIMIT 1"));
    }

    private SampleRunSource buildTemplateSource(
            AssignmentQuestionConfigInput config, ProgrammingLanguage programmingLanguage) {
        ProgrammingLanguage resolvedLanguage = resolveLanguage(programmingLanguage, config, null);
        List<ProgrammingSourceFile> templateFiles = config.templateFiles() == null ? List.of() : config.templateFiles();
        ProgrammingSourceSnapshot sourceSnapshot = ProgrammingSourceSnapshot.fromInput(
                resolvedLanguage, null, config.templateEntryFilePath(), templateFiles);
        return new SampleRunSource(
                resolvedLanguage,
                sourceSnapshot,
                normalizeDirectories(config.templateDirectories(), sourceSnapshot.files(), true),
                List.of(),
                null,
                null);
    }

    private boolean hasExplicitSnapshot(
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files,
            List<String> directories) {
        return StringUtils.hasText(codeText)
                || (artifactIds != null && !artifactIds.isEmpty())
                || programmingLanguage != null
                || StringUtils.hasText(entryFilePath)
                || (files != null && !files.isEmpty())
                || (directories != null && !directories.isEmpty());
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
            ProgrammingLanguage programmingLanguage,
            AssignmentQuestionConfigInput config,
            ProgrammingLanguage currentLanguage) {
        if (programmingLanguage != null) {
            return programmingLanguage;
        }
        if (currentLanguage != null) {
            return currentLanguage;
        }
        if (config.supportedLanguages() == null || config.supportedLanguages().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_REQUIRED", "样例试运行必须指定编程语言");
        }
        return config.supportedLanguages().getFirst();
    }

    private void validateProgrammingInput(
            AssignmentQuestionConfigInput config,
            List<SubmissionArtifactEntity> artifacts,
            ProgrammingLanguage programmingLanguage,
            ProgrammingSourceSnapshot sourceSnapshot,
            List<String> directories) {
        if (config.supportedLanguages() != null && !config.supportedLanguages().contains(programmingLanguage)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_UNSUPPORTED", "所选语言不在题目支持范围内");
        }
        validateDirectories(directories);
        validateSourceFiles(sourceSnapshot);
        validatePathCaseConflicts(sourceSnapshot, directories);
        int totalFileCount = artifacts.size() + sourceSnapshot.files().size();
        if (config.maxFileCount() != null && totalFileCount > config.maxFileCount()) {
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
            for (ProgrammingSourceFile file : sourceSnapshot.files()) {
                if (file.content().getBytes(StandardCharsets.UTF_8).length > maxFileSizeBytes) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_SIZE_EXCEEDED", "存在文件大小超过题目限制");
                }
            }
        }
        if (!Boolean.TRUE.equals(config.allowMultipleFiles()) && totalFileCount > 1) {
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
            for (ProgrammingSourceFile file : sourceSnapshot.files()) {
                if (!acceptedExtensions.contains(extensionOf(file.path()))) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_EXTENSION_INVALID", "存在文件扩展名不符合题目限制");
                }
            }
        }
    }

    private void validateDirectories(List<String> directories) {
        if (directories.size() > MAX_DIRECTORY_COUNT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_LIMIT_EXCEEDED", "样例试运行目录数量超过限制");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        LinkedHashMap<String, String> normalizedCasePaths = new LinkedHashMap<>();
        for (String directory : directories) {
            if (!isSafePath(directory)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_PATH_INVALID", "样例试运行目录路径不合法");
            }
            if (!normalized.add(directory)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_PATH_DUPLICATED", "样例试运行目录路径不能重复");
            }
            ensureCaseInsensitivePathAvailable(normalizedCasePaths, directory);
        }
    }

    private void validateSourceFiles(ProgrammingSourceSnapshot sourceSnapshot) {
        if (sourceSnapshot.files().size() > MAX_SOURCE_FILE_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_LIMIT_EXCEEDED", "样例试运行源码文件数量超过限制");
        }
        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<>();
        LinkedHashMap<String, String> normalizedCasePaths = new LinkedHashMap<>();
        for (ProgrammingSourceFile file : sourceSnapshot.files()) {
            if (!isSafePath(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_INVALID", "样例试运行源码文件路径不合法");
            }
            if (!normalizedPaths.add(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_DUPLICATED", "样例试运行源码文件路径不能重复");
            }
            if (file.content().length() > MAX_CODE_TEXT_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_CODE_TOO_LONG", "样例试运行代码正文长度超过限制");
            }
            ensureCaseInsensitivePathAvailable(normalizedCasePaths, file.path());
        }
        if (!sourceSnapshot.files().isEmpty() && !normalizedPaths.contains(sourceSnapshot.entryFilePath())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_ENTRY_FILE_INVALID", "样例试运行入口文件必须出现在源码文件列表中");
        }
    }

    private void validatePathCaseConflicts(ProgrammingSourceSnapshot sourceSnapshot, List<String> directories) {
        LinkedHashMap<String, String> normalizedPaths = new LinkedHashMap<>();
        for (ProgrammingSourceFile file : sourceSnapshot.files()) {
            ensureCaseInsensitivePathAvailable(normalizedPaths, file.path());
        }
        for (String directory : directories) {
            ensureCaseInsensitivePathAvailable(normalizedPaths, directory);
        }
    }

    private void ensureCaseInsensitivePathAvailable(Map<String, String> normalizedPaths, String path) {
        String normalizedKey = normalizePathKey(path);
        String existingPath = normalizedPaths.putIfAbsent(normalizedKey, path);
        if (existingPath != null && !Objects.equals(existingPath, path)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_CASE_CONFLICT", "样例试运行路径存在大小写冲突");
        }
    }

    private boolean isSafePath(String path) {
        return StringUtils.hasText(path)
                && path.length() <= MAX_SOURCE_FILE_PATH_LENGTH
                && !path.startsWith("/")
                && !path.endsWith("/")
                && !path.contains("\\")
                && !path.contains("//")
                && !path.contains("/./")
                && !path.startsWith("./")
                && !path.contains("../")
                && SAFE_SOURCE_FILE_PATH.matcher(path).matches();
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

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replace("\r\n", "\n");
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String normalizePathKey(String path) {
        return path == null ? null : path.toLowerCase(Locale.ROOT);
    }

    private String writeArtifactIds(List<Long> artifactIds) {
        try {
            return objectMapper.writeValueAsString(artifactIds == null ? List.of() : artifactIds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行附件列表无法序列化", exception);
        }
    }

    private String writeSourceFiles(List<ProgrammingSourceFile> files) {
        try {
            return objectMapper.writeValueAsString(files == null ? List.of() : files);
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行源码文件列表无法序列化", exception);
        }
    }

    private String writeDirectories(List<String> directories) {
        try {
            return objectMapper.writeValueAsString(directories == null ? List.of() : directories);
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行目录列表无法序列化", exception);
        }
    }

    private String writeDetailReport(JudgeJobStoredReport detailReport) {
        if (detailReport == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detailReport);
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行详细报告无法序列化", exception);
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

    private List<ProgrammingSourceFile> readSourceFiles(String sourceFilesJson) {
        if (!StringUtils.hasText(sourceFilesJson)) {
            return List.of();
        }
        try {
            ProgrammingSourceFile[] files = objectMapper.readValue(sourceFilesJson, ProgrammingSourceFile[].class);
            return files == null ? List.of() : Arrays.stream(files).toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行源码文件列表无法读取", exception);
        }
    }

    private List<String> readDirectories(String directoriesJson) {
        if (!StringUtils.hasText(directoriesJson)) {
            return List.of();
        }
        try {
            String[] directories = objectMapper.readValue(directoriesJson, String[].class);
            return directories == null ? List.of() : Arrays.stream(directories).toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("样例试运行目录列表无法读取", exception);
        }
    }

    private DetailReportLoadResult readDetailReport(ProgrammingSampleRunEntity entity) {
        try {
            return new DetailReportLoadResult(
                    judgeArtifactStorageService.loadProgrammingSampleRunDetailReport(entity), null);
        } catch (RuntimeException exception) {
            log.warn("样例试运行详细报告读取失败，回退到摘要视图，sampleRunId={}, error={}", entity.getId(), exception.getMessage());
            return new DetailReportLoadResult(null, resolveDetailReportUnavailableReasonCode(exception));
        }
    }

    private LinkedHashSet<String> normalizeExtensions(List<String> extensions) {
        return extensions.stream()
                .filter(StringUtils::hasText)
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> normalizeDirectories(
            List<String> directories, List<ProgrammingSourceFile> files, boolean includeParentDirectories) {
        TreeSet<String> normalized = new TreeSet<>();
        if (directories != null) {
            directories.stream().filter(StringUtils::hasText).map(String::trim).forEach(normalized::add);
        }
        if (includeParentDirectories) {
            for (ProgrammingSourceFile file : files) {
                normalized.addAll(parentDirectoriesOf(file.path()));
            }
        }
        return normalized.stream().toList();
    }

    private List<String> parentDirectoriesOf(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.contains("/")) {
            return List.of();
        }
        java.util.ArrayList<String> directories = new java.util.ArrayList<>();
        int index = filePath.indexOf('/');
        while (index > 0) {
            directories.add(filePath.substring(0, index));
            index = filePath.indexOf('/', index + 1);
        }
        return directories;
    }

    private String resolveDetailReportUnavailableReasonCode(RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("对象无法读取")) {
            return "DETAIL_REPORT_MISSING";
        }
        return "DETAIL_REPORT_UNAVAILABLE";
    }

    private record ProgrammingQuestionContext(AssignmentEntity assignment, AssignmentQuestionSnapshot question) {}

    private record ExecutionInput(String stdinText, String expectedStdout) {}

    private record SampleRunSource(
            ProgrammingLanguage programmingLanguage,
            ProgrammingSourceSnapshot sourceSnapshot,
            List<String> directories,
            List<Long> artifactIds,
            String lastStdinText,
            Long workspaceRevisionId) {}

    private record DetailReportLoadResult(JudgeJobStoredReport detailReport, String unavailableReasonCode) {}
}
