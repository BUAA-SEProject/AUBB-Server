package com.aubb.server.modules.submission.api;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceApplicationService;
import com.aubb.server.modules.submission.application.workspace.ProgrammingWorkspaceView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
                request.codeText(),
                request.artifactIds(),
                request.programmingLanguage(),
                principal);
    }

    public record SaveWorkspaceRequest(
            String codeText, List<Long> artifactIds, ProgrammingLanguage programmingLanguage) {}
}
