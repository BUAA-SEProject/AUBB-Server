package com.aubb.server.modules.submission.api;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceApplicationService;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceOperationInput;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceRevisionSummaryView;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceRevisionView;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceView;
import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceSaveKind;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}")
@RequiredArgsConstructor
public class MyProgrammingWorkspaceController {

    private final ProgrammingWorkspaceApplicationService programmingWorkspaceApplicationService;

    @GetMapping("/workspace")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingWorkspaceView getWorkspace(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.getMyWorkspace(assignmentId, questionId, principal);
    }

    @PutMapping("/workspace")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingWorkspaceView saveWorkspace(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @Valid @RequestBody SaveWorkspaceRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.saveMyWorkspace(
                assignmentId,
                questionId,
                request.baseRevisionId(),
                request.codeText(),
                request.artifactIds(),
                request.programmingLanguage(),
                request.entryFilePath(),
                request.files(),
                request.directories(),
                request.lastStdinText(),
                request.saveKind(),
                request.revisionMessage(),
                principal);
    }

    @PostMapping("/workspace/operations")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingWorkspaceView applyWorkspaceOperations(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @Valid @RequestBody ApplyWorkspaceOperationsRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.applyMyWorkspaceOperations(
                assignmentId,
                questionId,
                request.baseRevisionId(),
                request.operations(),
                request.lastStdinText(),
                request.revisionMessage(),
                principal);
    }

    @GetMapping("/workspace/revisions")
    @PreAuthorize("isAuthenticated()")
    public List<ProgrammingWorkspaceRevisionSummaryView> listWorkspaceRevisions(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.listMyWorkspaceRevisions(assignmentId, questionId, principal);
    }

    @GetMapping("/workspace/revisions/{revisionId}")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingWorkspaceRevisionView getWorkspaceRevision(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @PathVariable Long revisionId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.getMyWorkspaceRevision(
                assignmentId, questionId, revisionId, principal);
    }

    @PostMapping("/workspace/revisions/{revisionId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingWorkspaceView restoreWorkspaceRevision(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @PathVariable Long revisionId,
            @Valid @RequestBody RestoreWorkspaceRevisionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.restoreMyWorkspaceRevision(
                assignmentId, questionId, revisionId, request.baseRevisionId(), request.revisionMessage(), principal);
    }

    @PostMapping("/workspace/reset-to-template")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingWorkspaceView resetWorkspaceToTemplate(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @Valid @RequestBody ResetWorkspaceToTemplateRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingWorkspaceApplicationService.resetMyWorkspaceToTemplate(
                assignmentId,
                questionId,
                request.baseRevisionId(),
                request.programmingLanguage(),
                request.revisionMessage(),
                principal);
    }

    public record SaveWorkspaceRequest(
            Long baseRevisionId,
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files,
            List<String> directories,
            String lastStdinText,
            ProgrammingWorkspaceSaveKind saveKind,
            String revisionMessage) {}

    public record ApplyWorkspaceOperationsRequest(
            Long baseRevisionId,
            List<ProgrammingWorkspaceOperationInput> operations,
            String lastStdinText,
            String revisionMessage) {}

    public record RestoreWorkspaceRevisionRequest(Long baseRevisionId, String revisionMessage) {}

    public record ResetWorkspaceToTemplateRequest(
            Long baseRevisionId, ProgrammingLanguage programmingLanguage, String revisionMessage) {}
}
