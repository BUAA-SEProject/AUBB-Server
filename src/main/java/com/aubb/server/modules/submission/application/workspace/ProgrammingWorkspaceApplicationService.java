package com.aubb.server.modules.submission.application.workspace;

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
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResult;
import com.aubb.server.modules.identityaccess.application.authz.core.ReadPathAuthorizationService;
import com.aubb.server.modules.submission.application.SubmissionArtifactView;
import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceRevisionKind;
import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceSaveKind;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionArtifactMapper;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceEntity;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceMapper;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceRevisionEntity;
import com.aubb.server.modules.submission.infrastructure.workspace.ProgrammingWorkspaceRevisionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private static final int MAX_SOURCE_FILE_COUNT = 20;
    private static final int MAX_DIRECTORY_COUNT = 40;
    private static final int MAX_SOURCE_FILE_PATH_LENGTH = 200;
    private static final Pattern SAFE_SOURCE_FILE_PATH = Pattern.compile("^[A-Za-z0-9._/-]+$");

    private final ProgrammingWorkspaceMapper programmingWorkspaceMapper;
    private final ProgrammingWorkspaceRevisionMapper programmingWorkspaceRevisionMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final SubmissionArtifactMapper submissionArtifactMapper;
    private final ReadPathAuthorizationService readPathAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProgrammingWorkspaceView getMyWorkspace(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.read");
        WorkspaceState workspaceState = loadWorkspaceState(context, principal.getUserId());
        return toWorkspaceView(context.assignment(), context.question(), workspaceState, principal);
    }

    @Transactional
    public ProgrammingWorkspaceView saveMyWorkspace(
            Long assignmentId,
            Long questionId,
            Long baseRevisionId,
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files,
            List<String> directories,
            String lastStdinText,
            ProgrammingWorkspaceSaveKind saveKind,
            String revisionMessage,
            AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.save");
        WorkspaceState currentState = loadWorkspaceState(context, principal.getUserId());
        assertBaseRevision(currentState, baseRevisionId);
        List<Long> normalizedArtifactIds = normalizeArtifactIds(artifactIds);
        List<SubmissionArtifactEntity> artifacts =
                loadScopedArtifacts(assignmentId, principal.getUserId(), normalizedArtifactIds);
        ProgrammingLanguage resolvedLanguage =
                resolveLanguage(programmingLanguage, context.question().config(), currentState.programmingLanguage());
        ProgrammingSourceSnapshot sourceSnapshot = ProgrammingSourceSnapshot.fromInput(
                resolvedLanguage, normalizeCodeText(codeText), entryFilePath, files);
        List<String> normalizedDirectories = normalizeDirectories(directories, sourceSnapshot.files(), true);
        validateProgrammingInput(
                context.question().config(), artifacts, resolvedLanguage, sourceSnapshot, normalizedDirectories);
        String normalizedLastStdinText = normalizeStdinText(lastStdinText);
        ProgrammingWorkspaceRevisionKind revisionKind = saveKind == ProgrammingWorkspaceSaveKind.MANUAL
                ? ProgrammingWorkspaceRevisionKind.MANUAL_SAVE
                : ProgrammingWorkspaceRevisionKind.AUTO_SAVE;
        WorkspaceDraft draft = new WorkspaceDraft(
                resolvedLanguage,
                sourceSnapshot,
                normalizedDirectories,
                normalizedArtifactIds,
                normalizedLastStdinText);
        if (revisionKind == ProgrammingWorkspaceRevisionKind.AUTO_SAVE
                && !hasWorkspaceStateChanged(currentState, draft)) {
            return toWorkspaceView(context.assignment(), context.question(), currentState, principal);
        }
        WorkspaceState updatedState =
                persistWorkspace(context, currentState, draft, revisionKind, revisionMessage, principal);
        return toWorkspaceView(context.assignment(), context.question(), updatedState, principal);
    }

    @Transactional
    public ProgrammingWorkspaceView applyMyWorkspaceOperations(
            Long assignmentId,
            Long questionId,
            Long baseRevisionId,
            List<ProgrammingWorkspaceOperationInput> operations,
            String lastStdinText,
            String revisionMessage,
            AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.save");
        if (operations == null || operations.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_OPERATIONS_REQUIRED", "工作区操作不能为空");
        }
        WorkspaceState currentState = loadWorkspaceState(context, principal.getUserId());
        assertBaseRevision(currentState, baseRevisionId);
        MutableWorkspace workspace = MutableWorkspace.fromState(currentState);
        for (ProgrammingWorkspaceOperationInput operation : operations) {
            applyOperation(workspace, operation);
        }
        WorkspaceDraft draft = workspace.toDraft(
                normalizeStdinText(lastStdinText) != null
                        ? normalizeStdinText(lastStdinText)
                        : currentState.lastStdinText());
        List<SubmissionArtifactEntity> artifacts =
                loadScopedArtifacts(assignmentId, principal.getUserId(), draft.artifactIds());
        validateProgrammingInput(
                context.question().config(),
                artifacts,
                draft.programmingLanguage(),
                draft.sourceSnapshot(),
                draft.directories());
        WorkspaceState updatedState = persistWorkspace(
                context,
                currentState,
                draft,
                ProgrammingWorkspaceRevisionKind.FILE_OPERATION,
                revisionMessage,
                principal);
        return toWorkspaceView(context.assignment(), context.question(), updatedState, principal);
    }

    @Transactional(readOnly = true)
    public List<ProgrammingWorkspaceRevisionSummaryView> listMyWorkspaceRevisions(
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.read");
        ProgrammingWorkspaceEntity entity = loadWorkspaceEntity(questionId, principal.getUserId());
        if (entity == null) {
            return List.of();
        }
        return programmingWorkspaceRevisionMapper
                .selectList(Wrappers.<ProgrammingWorkspaceRevisionEntity>lambdaQuery()
                        .eq(ProgrammingWorkspaceRevisionEntity::getWorkspaceId, entity.getId())
                        .eq(ProgrammingWorkspaceRevisionEntity::getAssignmentQuestionId, questionId)
                        .eq(ProgrammingWorkspaceRevisionEntity::getUserId, principal.getUserId())
                        .orderByDesc(ProgrammingWorkspaceRevisionEntity::getRevisionNo)
                        .orderByDesc(ProgrammingWorkspaceRevisionEntity::getId))
                .stream()
                .map(this::toRevisionSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgrammingWorkspaceRevisionView getMyWorkspaceRevision(
            Long assignmentId, Long questionId, Long revisionId, AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.read");
        ProgrammingWorkspaceRevisionEntity revision =
                requireWorkspaceRevision(assignmentId, questionId, revisionId, principal.getUserId());
        return toRevisionView(context.assignment(), context.question(), revision);
    }

    @Transactional
    public ProgrammingWorkspaceView restoreMyWorkspaceRevision(
            Long assignmentId,
            Long questionId,
            Long revisionId,
            Long baseRevisionId,
            String revisionMessage,
            AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.save");
        WorkspaceState currentState = loadWorkspaceState(context, principal.getUserId());
        assertBaseRevision(currentState, baseRevisionId);
        ProgrammingWorkspaceRevisionEntity revision =
                requireWorkspaceRevision(assignmentId, questionId, revisionId, principal.getUserId());
        WorkspaceDraft draft = new WorkspaceDraft(
                ProgrammingLanguage.valueOf(revision.getProgrammingLanguage()),
                ProgrammingSourceSnapshot.fromInput(
                        ProgrammingLanguage.valueOf(revision.getProgrammingLanguage()),
                        revision.getCodeText(),
                        revision.getEntryFilePath(),
                        readSourceFiles(revision.getSourceFilesJson())),
                readDirectories(revision.getSourceDirectoriesJson()),
                readArtifactIds(revision.getArtifactIdsJson()),
                revision.getLastStdinText());
        List<SubmissionArtifactEntity> artifacts =
                loadScopedArtifacts(assignmentId, principal.getUserId(), draft.artifactIds());
        validateProgrammingInput(
                context.question().config(),
                artifacts,
                draft.programmingLanguage(),
                draft.sourceSnapshot(),
                draft.directories());
        WorkspaceState restoredState = persistWorkspace(
                context, currentState, draft, ProgrammingWorkspaceRevisionKind.RESTORE, revisionMessage, principal);
        return toWorkspaceView(context.assignment(), context.question(), restoredState, principal);
    }

    @Transactional
    public ProgrammingWorkspaceView resetMyWorkspaceToTemplate(
            Long assignmentId,
            Long questionId,
            Long baseRevisionId,
            ProgrammingLanguage programmingLanguage,
            String revisionMessage,
            AuthenticatedUserPrincipal principal) {
        ProgrammingQuestionContext context =
                requireVisibleProgrammingQuestion(assignmentId, questionId, principal, "ide.save");
        WorkspaceState currentState = loadWorkspaceState(context, principal.getUserId());
        assertBaseRevision(currentState, baseRevisionId);
        WorkspaceDraft templateDraft = buildTemplateDraft(
                context.question().config(),
                resolveLanguage(programmingLanguage, context.question().config(), null));
        if (templateDraft.sourceSnapshot().files().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_TEMPLATE_MISSING", "当前编程题未配置模板工程");
        }
        WorkspaceState resetState = persistWorkspace(
                context,
                currentState,
                templateDraft,
                ProgrammingWorkspaceRevisionKind.TEMPLATE_RESET,
                revisionMessage,
                principal);
        return toWorkspaceView(context.assignment(), context.question(), resetState, principal);
    }

    private WorkspaceState loadWorkspaceState(ProgrammingQuestionContext context, Long userId) {
        ProgrammingWorkspaceEntity entity =
                loadWorkspaceEntity(context.question().id(), userId);
        if (entity == null) {
            return buildTemplateState(context.question().config());
        }
        ProgrammingWorkspaceRevisionEntity latestRevision = loadLatestRevision(entity.getId());
        ProgrammingLanguage programmingLanguage = ProgrammingLanguage.valueOf(entity.getProgrammingLanguage());
        ProgrammingSourceSnapshot sourceSnapshot = ProgrammingSourceSnapshot.fromInput(
                programmingLanguage,
                entity.getCodeText(),
                entity.getEntryFilePath(),
                readSourceFiles(entity.getSourceFilesJson()));
        return new WorkspaceState(
                entity,
                latestRevision,
                programmingLanguage,
                sourceSnapshot,
                normalizeDirectories(readDirectories(entity.getSourceDirectoriesJson()), sourceSnapshot.files(), true),
                readArtifactIds(entity.getArtifactIdsJson()),
                entity.getLastStdinText());
    }

    private WorkspaceState buildTemplateState(AssignmentQuestionConfigInput config) {
        ProgrammingLanguage programmingLanguage = defaultLanguage(config);
        WorkspaceDraft templateDraft = buildTemplateDraft(config, programmingLanguage);
        return new WorkspaceState(
                null,
                null,
                templateDraft.programmingLanguage(),
                templateDraft.sourceSnapshot(),
                templateDraft.directories(),
                templateDraft.artifactIds(),
                templateDraft.lastStdinText());
    }

    private WorkspaceDraft buildTemplateDraft(
            AssignmentQuestionConfigInput config, ProgrammingLanguage programmingLanguage) {
        ProgrammingLanguage resolvedLanguage =
                programmingLanguage == null ? defaultLanguage(config) : programmingLanguage;
        List<ProgrammingSourceFile> templateFiles =
                config == null || config.templateFiles() == null ? List.of() : config.templateFiles();
        ProgrammingSourceSnapshot sourceSnapshot = ProgrammingSourceSnapshot.fromInput(
                resolvedLanguage, null, config == null ? null : config.templateEntryFilePath(), templateFiles);
        List<String> directories = normalizeDirectories(
                config == null ? List.of() : config.templateDirectories(), sourceSnapshot.files(), true);
        return new WorkspaceDraft(resolvedLanguage, sourceSnapshot, directories, List.of(), null);
    }

    private ProgrammingWorkspaceEntity loadWorkspaceEntity(Long questionId, Long userId) {
        return programmingWorkspaceMapper.selectOne(Wrappers.<ProgrammingWorkspaceEntity>lambdaQuery()
                .eq(ProgrammingWorkspaceEntity::getAssignmentQuestionId, questionId)
                .eq(ProgrammingWorkspaceEntity::getUserId, userId)
                .last("LIMIT 1"));
    }

    private ProgrammingWorkspaceRevisionEntity loadLatestRevision(Long workspaceId) {
        if (workspaceId == null) {
            return null;
        }
        return programmingWorkspaceRevisionMapper.selectOne(Wrappers.<ProgrammingWorkspaceRevisionEntity>lambdaQuery()
                .eq(ProgrammingWorkspaceRevisionEntity::getWorkspaceId, workspaceId)
                .orderByDesc(ProgrammingWorkspaceRevisionEntity::getRevisionNo)
                .orderByDesc(ProgrammingWorkspaceRevisionEntity::getId)
                .last("LIMIT 1"));
    }

    private ProgrammingWorkspaceRevisionEntity requireWorkspaceRevision(
            Long assignmentId, Long questionId, Long revisionId, Long userId) {
        ProgrammingWorkspaceRevisionEntity revision = programmingWorkspaceRevisionMapper.selectById(revisionId);
        if (revision == null
                || !Objects.equals(revision.getAssignmentId(), assignmentId)
                || !Objects.equals(revision.getAssignmentQuestionId(), questionId)
                || !Objects.equals(revision.getUserId(), userId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "PROGRAMMING_WORKSPACE_REVISION_NOT_FOUND", "工作区历史版本不存在");
        }
        return revision;
    }

    private ProgrammingWorkspaceView toWorkspaceView(
            AssignmentEntity assignment,
            AssignmentQuestionSnapshot question,
            WorkspaceState workspaceState,
            AuthenticatedUserPrincipal principal) {
        AuthorizationResult editAuthorization =
                readPathAuthorizationService.authorizeMyAssignmentCapability(principal, "ide.save", assignment);
        AuthorizationResult runAuthorization =
                readPathAuthorizationService.authorizeMyAssignmentCapability(principal, "ide.run", assignment);
        List<SubmissionArtifactView> artifacts = loadArtifactViews(workspaceState.artifactIds());
        return new ProgrammingWorkspaceView(
                assignment.getId(),
                question.id(),
                workspaceState.programmingLanguage(),
                workspaceState.sourceSnapshot().entryCodeText(),
                workspaceState.sourceSnapshot().entryFilePath(),
                workspaceState.sourceSnapshot().files(),
                workspaceState.directories(),
                workspaceState.artifactIds(),
                artifacts,
                workspaceState.lastStdinText(),
                workspaceState.latestRevision() == null
                        ? null
                        : workspaceState.latestRevision().getId(),
                workspaceState.latestRevision() == null
                        ? null
                        : workspaceState.latestRevision().getRevisionNo(),
                workspaceState.latestRevision() == null
                        ? null
                        : ProgrammingWorkspaceRevisionKind.valueOf(
                                workspaceState.latestRevision().getRevisionKind()),
                editAuthorization.allowed(),
                editAuthorization.allowed() ? null : editAuthorization.reasonCode(),
                runAuthorization.allowed(),
                runAuthorization.allowed() ? null : runAuthorization.reasonCode(),
                workspaceState.entity() == null ? null : workspaceState.entity().getUpdatedAt());
    }

    private void assertBaseRevision(WorkspaceState currentState, Long baseRevisionId) {
        if (baseRevisionId == null) {
            return;
        }
        Long latestRevisionId = currentState.latestRevision() == null
                ? null
                : currentState.latestRevision().getId();
        if (!Objects.equals(baseRevisionId, latestRevisionId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "PROGRAMMING_WORKSPACE_CONFLICT", "工作区已被其他会话更新，请刷新后重试");
        }
    }

    private boolean hasWorkspaceStateChanged(WorkspaceState currentState, WorkspaceDraft draft) {
        return !Objects.equals(currentState.programmingLanguage(), draft.programmingLanguage())
                || !Objects.equals(currentState.sourceSnapshot(), draft.sourceSnapshot())
                || !Objects.equals(currentState.directories(), draft.directories())
                || !Objects.equals(currentState.artifactIds(), draft.artifactIds())
                || !Objects.equals(currentState.lastStdinText(), draft.lastStdinText());
    }

    private ProgrammingWorkspaceRevisionSummaryView toRevisionSummaryView(ProgrammingWorkspaceRevisionEntity revision) {
        List<ProgrammingSourceFile> files = readSourceFiles(revision.getSourceFilesJson());
        List<String> directories = readDirectories(revision.getSourceDirectoriesJson());
        List<Long> artifactIds = readArtifactIds(revision.getArtifactIdsJson());
        return new ProgrammingWorkspaceRevisionSummaryView(
                revision.getId(),
                revision.getRevisionNo(),
                ProgrammingWorkspaceRevisionKind.valueOf(revision.getRevisionKind()),
                revision.getRevisionMessage(),
                ProgrammingLanguage.valueOf(revision.getProgrammingLanguage()),
                revision.getEntryFilePath(),
                files.size(),
                directories.size(),
                artifactIds.size(),
                revision.getCreatedAt());
    }

    private ProgrammingWorkspaceRevisionView toRevisionView(
            AssignmentEntity assignment,
            AssignmentQuestionSnapshot question,
            ProgrammingWorkspaceRevisionEntity revision) {
        List<Long> artifactIds = readArtifactIds(revision.getArtifactIdsJson());
        return new ProgrammingWorkspaceRevisionView(
                revision.getId(),
                revision.getRevisionNo(),
                ProgrammingWorkspaceRevisionKind.valueOf(revision.getRevisionKind()),
                revision.getRevisionMessage(),
                assignment.getId(),
                question.id(),
                ProgrammingLanguage.valueOf(revision.getProgrammingLanguage()),
                ProgrammingSourceSnapshot.fromInput(
                                ProgrammingLanguage.valueOf(revision.getProgrammingLanguage()),
                                revision.getCodeText(),
                                revision.getEntryFilePath(),
                                readSourceFiles(revision.getSourceFilesJson()))
                        .entryCodeText(),
                revision.getEntryFilePath(),
                readSourceFiles(revision.getSourceFilesJson()),
                readDirectories(revision.getSourceDirectoriesJson()),
                artifactIds,
                loadArtifactViews(artifactIds),
                revision.getLastStdinText(),
                revision.getCreatedAt());
    }

    private WorkspaceState persistWorkspace(
            ProgrammingQuestionContext context,
            WorkspaceState currentState,
            WorkspaceDraft draft,
            ProgrammingWorkspaceRevisionKind revisionKind,
            String revisionMessage,
            AuthenticatedUserPrincipal principal) {
        ProgrammingWorkspaceEntity entity = currentState.entity();
        if (entity == null) {
            entity = new ProgrammingWorkspaceEntity();
            entity.setAssignmentId(context.assignment().getId());
            entity.setAssignmentQuestionId(context.question().id());
            entity.setUserId(principal.getUserId());
        }
        entity.setProgrammingLanguage(draft.programmingLanguage().name());
        entity.setCodeText(draft.sourceSnapshot().entryCodeText());
        entity.setArtifactIdsJson(writeArtifactIds(draft.artifactIds()));
        entity.setEntryFilePath(draft.sourceSnapshot().entryFilePath());
        entity.setSourceFilesJson(writeSourceFiles(draft.sourceSnapshot().files()));
        entity.setSourceDirectoriesJson(writeDirectories(draft.directories()));
        entity.setLastStdinText(draft.lastStdinText());
        if (entity.getId() == null) {
            programmingWorkspaceMapper.insert(entity);
        } else {
            programmingWorkspaceMapper.updateById(entity);
        }
        ProgrammingWorkspaceRevisionEntity revision = new ProgrammingWorkspaceRevisionEntity();
        revision.setWorkspaceId(entity.getId());
        revision.setAssignmentId(context.assignment().getId());
        revision.setAssignmentQuestionId(context.question().id());
        revision.setUserId(principal.getUserId());
        revision.setRevisionNo(nextRevisionNo(entity.getId()));
        revision.setRevisionKind(revisionKind.name());
        revision.setRevisionMessage(normalizeRevisionMessage(revisionMessage));
        revision.setProgrammingLanguage(draft.programmingLanguage().name());
        revision.setCodeText(draft.sourceSnapshot().entryCodeText());
        revision.setArtifactIdsJson(writeArtifactIds(draft.artifactIds()));
        revision.setEntryFilePath(draft.sourceSnapshot().entryFilePath());
        revision.setSourceFilesJson(writeSourceFiles(draft.sourceSnapshot().files()));
        revision.setSourceDirectoriesJson(writeDirectories(draft.directories()));
        revision.setLastStdinText(draft.lastStdinText());
        programmingWorkspaceRevisionMapper.insert(revision);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.PROGRAMMING_WORKSPACE_SAVED,
                "PROGRAMMING_WORKSPACE",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "assignmentId",
                        context.assignment().getId(),
                        "assignmentQuestionId",
                        context.question().id(),
                        "artifactCount",
                        draft.artifactIds().size(),
                        "sourceFileCount",
                        draft.sourceSnapshot().files().size(),
                        "directoryCount",
                        draft.directories().size(),
                        "entryFilePath",
                        draft.sourceSnapshot().entryFilePath(),
                        "programmingLanguage",
                        draft.programmingLanguage().name(),
                        "revisionKind",
                        revisionKind.name(),
                        "revisionNo",
                        revision.getRevisionNo()));
        entity = programmingWorkspaceMapper.selectById(entity.getId());
        return new WorkspaceState(
                entity,
                revision,
                draft.programmingLanguage(),
                draft.sourceSnapshot(),
                draft.directories(),
                draft.artifactIds(),
                draft.lastStdinText());
    }

    private long nextRevisionNo(Long workspaceId) {
        ProgrammingWorkspaceRevisionEntity latestRevision = loadLatestRevision(workspaceId);
        return latestRevision == null ? 1L : latestRevision.getRevisionNo() + 1L;
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
            Long assignmentId, Long questionId, AuthenticatedUserPrincipal principal, String permissionCode) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "作业不存在");
        }
        if (!readPathAuthorizationService.canAccessMyAssignmentCapability(principal, "task.read", assignment)
                || !readPathAuthorizationService.canAccessMyAssignmentCapability(
                        principal, permissionCode, assignment)) {
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
            ProgrammingLanguage programmingLanguage,
            AssignmentQuestionConfigInput config,
            ProgrammingLanguage currentLanguage) {
        if (programmingLanguage != null) {
            return programmingLanguage;
        }
        if (currentLanguage != null) {
            return currentLanguage;
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
            ProgrammingLanguage programmingLanguage,
            ProgrammingSourceSnapshot sourceSnapshot,
            List<String> directories) {
        if (programmingLanguage == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_LANGUAGE_REQUIRED", "编程题工作区必须指定编程语言");
        }
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_LIMIT_EXCEEDED", "工作区目录数量超过限制");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        LinkedHashMap<String, String> normalizedCasePaths = new LinkedHashMap<>();
        for (String directory : directories) {
            if (!isSafePath(directory)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_PATH_INVALID", "工作区目录路径不合法");
            }
            if (!normalized.add(directory)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_PATH_DUPLICATED", "工作区目录路径不能重复");
            }
            ensureCaseInsensitivePathAvailable(normalizedCasePaths, directory);
        }
    }

    private void validateSourceFiles(ProgrammingSourceSnapshot sourceSnapshot) {
        if (sourceSnapshot.files().size() > MAX_SOURCE_FILE_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_FILE_LIMIT_EXCEEDED", "工作区源码文件数量超过限制");
        }
        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<>();
        LinkedHashMap<String, String> normalizedCasePaths = new LinkedHashMap<>();
        for (ProgrammingSourceFile file : sourceSnapshot.files()) {
            if (!isSafePath(file.path())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_INVALID", "工作区源码文件路径不合法");
            }
            if (!normalizedPaths.add(file.path())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_DUPLICATED", "工作区源码文件路径不能重复");
            }
            if (file.content().length() > MAX_CODE_TEXT_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_CODE_TOO_LONG", "编程题工作区代码正文长度超过限制");
            }
            ensureCaseInsensitivePathAvailable(normalizedCasePaths, file.path());
        }
        if (!sourceSnapshot.files().isEmpty() && !normalizedPaths.contains(sourceSnapshot.entryFilePath())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_ENTRY_FILE_INVALID", "工作区入口文件必须出现在源码文件列表中");
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
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_CASE_CONFLICT", "工作区路径存在大小写冲突");
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_CODE_TOO_LONG", "编程题工作区代码正文长度超过限制");
        }
        return normalized;
    }

    private String normalizeStdinText(String stdinText) {
        if (!StringUtils.hasText(stdinText)) {
            return null;
        }
        return stdinText.replace("\r\n", "\n");
    }

    private String normalizeRevisionMessage(String revisionMessage) {
        if (!StringUtils.hasText(revisionMessage)) {
            return null;
        }
        String normalized = revisionMessage.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private void applyOperation(MutableWorkspace workspace, ProgrammingWorkspaceOperationInput operation) {
        if (operation == null || operation.type() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_OPERATION_INVALID", "工作区操作缺少类型");
        }
        switch (operation.type()) {
            case CREATE_FILE ->
                workspace.createFile(requirePath(operation.path()), normalizeFileContent(operation.content()));
            case UPDATE_FILE ->
                workspace.updateFile(requirePath(operation.path()), normalizeFileContent(operation.content()));
            case CREATE_DIRECTORY -> workspace.createDirectory(requirePath(operation.path()));
            case RENAME_PATH ->
                workspace.renamePath(requirePath(operation.path()), requireNewPath(operation.newPath()));
            case DELETE_PATH -> workspace.deletePath(requirePath(operation.path()));
            default ->
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_OPERATION_UNSUPPORTED", "当前工作区操作暂不支持");
        }
    }

    private String requirePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_PATH_REQUIRED", "工作区路径不能为空");
        }
        String normalized = path.trim();
        if (!isSafePath(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_INVALID", "工作区路径不合法");
        }
        return normalized;
    }

    private String requireNewPath(String newPath) {
        if (!StringUtils.hasText(newPath)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PROGRAMMING_WORKSPACE_NEW_PATH_REQUIRED", "工作区目标路径不能为空");
        }
        String normalized = newPath.trim();
        if (!isSafePath(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_INVALID", "工作区目标路径不合法");
        }
        return normalized;
    }

    private String normalizeFileContent(String content) {
        return content == null ? "" : content;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private static String normalizePathKey(String path) {
        return path == null ? null : path.toLowerCase(Locale.ROOT);
    }

    private String writeArtifactIds(List<Long> artifactIds) {
        try {
            return objectMapper.writeValueAsString(artifactIds == null ? List.of() : artifactIds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("工作区附件列表无法序列化", exception);
        }
    }

    private String writeSourceFiles(List<ProgrammingSourceFile> files) {
        try {
            return objectMapper.writeValueAsString(files == null ? List.of() : files);
        } catch (JacksonException exception) {
            throw new IllegalStateException("工作区源码文件列表无法序列化", exception);
        }
    }

    private String writeDirectories(List<String> directories) {
        try {
            return objectMapper.writeValueAsString(directories == null ? List.of() : directories);
        } catch (JacksonException exception) {
            throw new IllegalStateException("工作区目录列表无法序列化", exception);
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

    private List<ProgrammingSourceFile> readSourceFiles(String sourceFilesJson) {
        if (!StringUtils.hasText(sourceFilesJson)) {
            return List.of();
        }
        try {
            ProgrammingSourceFile[] files = objectMapper.readValue(sourceFilesJson, ProgrammingSourceFile[].class);
            return files == null ? List.of() : Arrays.stream(files).toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("工作区源码文件列表无法读取", exception);
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
            throw new IllegalStateException("工作区目录列表无法读取", exception);
        }
    }

    private LinkedHashSet<String> normalizeExtensions(List<String> extensions) {
        return extensions.stream()
                .filter(StringUtils::hasText)
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> normalizeDirectories(
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

    private static List<String> parentDirectoriesOf(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.contains("/")) {
            return List.of();
        }
        List<String> directories = new ArrayList<>();
        int index = filePath.indexOf('/');
        while (index > 0) {
            directories.add(filePath.substring(0, index));
            index = filePath.indexOf('/', index + 1);
        }
        return directories;
    }

    private static boolean isDescendantPath(String path, String newPath) {
        return StringUtils.hasText(path)
                && StringUtils.hasText(newPath)
                && normalizePathKey(newPath).startsWith(normalizePathKey(path) + "/");
    }

    private record ProgrammingQuestionContext(AssignmentEntity assignment, AssignmentQuestionSnapshot question) {}

    private record WorkspaceDraft(
            ProgrammingLanguage programmingLanguage,
            ProgrammingSourceSnapshot sourceSnapshot,
            List<String> directories,
            List<Long> artifactIds,
            String lastStdinText) {}

    private record WorkspaceState(
            ProgrammingWorkspaceEntity entity,
            ProgrammingWorkspaceRevisionEntity latestRevision,
            ProgrammingLanguage programmingLanguage,
            ProgrammingSourceSnapshot sourceSnapshot,
            List<String> directories,
            List<Long> artifactIds,
            String lastStdinText) {}

    private static final class MutableWorkspace {

        private final ProgrammingLanguage programmingLanguage;
        private final LinkedHashMap<String, String> files;
        private final LinkedHashSet<String> directories;
        private final List<Long> artifactIds;
        private String entryFilePath;

        private MutableWorkspace(
                ProgrammingLanguage programmingLanguage,
                LinkedHashMap<String, String> files,
                LinkedHashSet<String> directories,
                List<Long> artifactIds,
                String entryFilePath) {
            this.programmingLanguage = programmingLanguage;
            this.files = files;
            this.directories = directories;
            this.artifactIds = artifactIds;
            this.entryFilePath = entryFilePath;
        }

        static MutableWorkspace fromState(WorkspaceState state) {
            LinkedHashMap<String, String> files = new LinkedHashMap<>();
            for (ProgrammingSourceFile file : state.sourceSnapshot().files()) {
                files.put(file.path(), file.content());
            }
            return new MutableWorkspace(
                    state.programmingLanguage(),
                    files,
                    new LinkedHashSet<>(state.directories()),
                    List.copyOf(state.artifactIds()),
                    state.sourceSnapshot().entryFilePath());
        }

        void createFile(String path, String content) {
            ensureNoConflict(path);
            files.put(path, content);
            addParentDirectories(path);
            if (!StringUtils.hasText(entryFilePath)) {
                entryFilePath = path;
            }
        }

        void updateFile(String path, String content) {
            if (!files.containsKey(path)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_MISSING", "待更新源码文件不存在");
            }
            files.put(path, content);
        }

        void createDirectory(String path) {
            ensureNoConflict(path);
            directories.add(path);
            directories.addAll(parentDirectoriesOf(path));
        }

        void renamePath(String path, String newPath) {
            if (Objects.equals(path, newPath)) {
                return;
            }
            if (files.containsKey(path)) {
                ensureNoConflict(newPath);
                String content = files.remove(path);
                files.put(newPath, content);
                addParentDirectories(newPath);
                if (Objects.equals(entryFilePath, path)) {
                    entryFilePath = newPath;
                }
                return;
            }
            boolean hasDirectoryMatch = directories.contains(path) || hasNestedPath(path);
            if (!hasDirectoryMatch) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_MISSING", "待重命名路径不存在");
            }
            ensureRenameTargetAvailable(path, newPath);
            LinkedHashMap<String, String> renamedFiles = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String existingPath = entry.getKey();
                if (existingPath.equals(path) || existingPath.startsWith(path + "/")) {
                    renamedFiles.put(newPath + existingPath.substring(path.length()), entry.getValue());
                } else {
                    renamedFiles.put(existingPath, entry.getValue());
                }
            }
            files.clear();
            files.putAll(renamedFiles);
            LinkedHashSet<String> renamedDirectories = new LinkedHashSet<>();
            for (String directory : directories) {
                if (directory.equals(path) || directory.startsWith(path + "/")) {
                    renamedDirectories.add(newPath + directory.substring(path.length()));
                } else {
                    renamedDirectories.add(directory);
                }
            }
            directories.clear();
            directories.addAll(renamedDirectories);
            directories.addAll(parentDirectoriesOf(newPath));
            if (Objects.equals(entryFilePath, path) || entryFilePath.startsWith(path + "/")) {
                entryFilePath = newPath + entryFilePath.substring(path.length());
            }
        }

        void deletePath(String path) {
            boolean removedFile = files.remove(path) != null;
            boolean removedDirectory =
                    directories.removeIf(directory -> directory.equals(path) || directory.startsWith(path + "/"));
            boolean removedNestedFiles = false;
            if (!removedFile) {
                removedNestedFiles =
                        files.entrySet().removeIf(entry -> entry.getKey().startsWith(path + "/"));
            }
            if (!removedFile && !removedDirectory && !removedNestedFiles) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_MISSING", "待删除路径不存在");
            }
            if (StringUtils.hasText(entryFilePath)
                    && (Objects.equals(entryFilePath, path) || entryFilePath.startsWith(path + "/"))) {
                entryFilePath = files.isEmpty()
                        ? ProgrammingSourceSnapshot.defaultEntryFilePath(programmingLanguage)
                        : files.keySet().iterator().next();
            }
            directories.addAll(collectParentDirectoriesFromFiles());
        }

        WorkspaceDraft toDraft(String lastStdinText) {
            List<ProgrammingSourceFile> fileList = files.entrySet().stream()
                    .map(entry -> new ProgrammingSourceFile(entry.getKey(), entry.getValue()))
                    .toList();
            ProgrammingSourceSnapshot sourceSnapshot =
                    ProgrammingSourceSnapshot.fromInput(programmingLanguage, null, entryFilePath, fileList);
            List<String> normalizedDirectories = normalizeDirectories(new ArrayList<>(directories), fileList, true);
            return new WorkspaceDraft(
                    programmingLanguage, sourceSnapshot, normalizedDirectories, artifactIds, lastStdinText);
        }

        private void ensureNoConflict(String path) {
            if (files.containsKey(path) || directories.contains(path)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_DUPLICATED", "工作区路径已存在");
            }
            if (hasCaseInsensitiveConflict(path, null)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_CASE_CONFLICT", "工作区路径存在大小写冲突");
            }
        }

        private void ensureRenameTargetAvailable(String oldPath, String newPath) {
            if (isDescendantPath(oldPath, newPath)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_PATH_RENAME_DESCENDANT_INVALID", "目录不能重命名到自身子路径下");
            }
            for (String filePath : files.keySet()) {
                if (!filePath.equals(oldPath)
                        && !filePath.startsWith(oldPath + "/")
                        && (filePath.equals(newPath) || filePath.startsWith(newPath + "/"))) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_DUPLICATED", "目标路径已存在");
                }
            }
            for (String directory : directories) {
                if (!directory.equals(oldPath)
                        && !directory.startsWith(oldPath + "/")
                        && (directory.equals(newPath) || directory.startsWith(newPath + "/"))) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "PROGRAMMING_DIRECTORY_PATH_DUPLICATED", "目标目录路径已存在");
                }
            }
            if (hasCaseInsensitiveConflict(newPath, oldPath)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "PROGRAMMING_SOURCE_PATH_CASE_CONFLICT", "工作区路径存在大小写冲突");
            }
        }

        private boolean hasNestedPath(String path) {
            return files.keySet().stream().anyMatch(existingPath -> existingPath.startsWith(path + "/"))
                    || directories.stream().anyMatch(directory -> directory.startsWith(path + "/"));
        }

        private void addParentDirectories(String path) {
            directories.addAll(parentDirectoriesOf(path));
        }

        private List<String> collectParentDirectoriesFromFiles() {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String filePath : files.keySet()) {
                normalized.addAll(parentDirectoriesOf(filePath));
            }
            return normalized.stream().toList();
        }

        private boolean hasCaseInsensitiveConflict(String targetPath, String excludedRootPath) {
            String normalizedTarget = normalizePathKey(targetPath);
            String normalizedPrefix = normalizedTarget + "/";
            for (String filePath : files.keySet()) {
                if (isUnderExcludedRoot(filePath, excludedRootPath)) {
                    continue;
                }
                String normalizedFilePath = normalizePathKey(filePath);
                if (Objects.equals(normalizedFilePath, normalizedTarget)
                        || normalizedFilePath.startsWith(normalizedPrefix)) {
                    return true;
                }
            }
            for (String directory : directories) {
                if (isUnderExcludedRoot(directory, excludedRootPath)) {
                    continue;
                }
                String normalizedDirectoryPath = normalizePathKey(directory);
                if (Objects.equals(normalizedDirectoryPath, normalizedTarget)
                        || normalizedDirectoryPath.startsWith(normalizedPrefix)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isUnderExcludedRoot(String path, String excludedRootPath) {
            return StringUtils.hasText(excludedRootPath)
                    && (Objects.equals(path, excludedRootPath) || path.startsWith(excludedRootPath + "/"));
        }
    }
}
