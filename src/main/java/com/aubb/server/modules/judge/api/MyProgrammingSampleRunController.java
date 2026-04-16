package com.aubb.server.modules.judge.api;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.sample.ProgrammingSampleRunApplicationService;
import com.aubb.server.modules.judge.application.sample.ProgrammingSampleRunView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}")
@RequiredArgsConstructor
public class MyProgrammingSampleRunController {

    private final ProgrammingSampleRunApplicationService programmingSampleRunApplicationService;

    @PostMapping("/sample-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ProgrammingSampleRunView createSampleRun(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @Valid @RequestBody CreateProgrammingSampleRunRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingSampleRunApplicationService.createMySampleRun(
                assignmentId,
                questionId,
                request.codeText(),
                request.artifactIds(),
                request.programmingLanguage(),
                request.entryFilePath(),
                request.files(),
                request.directories(),
                request.stdinText(),
                request.expectedStdout(),
                request.useWorkspaceSnapshot(),
                request.workspaceRevisionId(),
                principal);
    }

    @GetMapping("/sample-runs")
    @PreAuthorize("isAuthenticated()")
    public List<ProgrammingSampleRunView> listSampleRuns(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingSampleRunApplicationService.listMySampleRuns(assignmentId, questionId, principal);
    }

    @GetMapping("/sample-runs/{sampleRunId}")
    @PreAuthorize("isAuthenticated()")
    public ProgrammingSampleRunView getSampleRun(
            @PathVariable Long assignmentId,
            @PathVariable Long questionId,
            @PathVariable Long sampleRunId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return programmingSampleRunApplicationService.getMySampleRun(assignmentId, questionId, sampleRunId, principal);
    }

    public record CreateProgrammingSampleRunRequest(
            String codeText,
            List<Long> artifactIds,
            ProgrammingLanguage programmingLanguage,
            String entryFilePath,
            List<ProgrammingSourceFile> files,
            List<String> directories,
            String stdinText,
            String expectedStdout,
            Boolean useWorkspaceSnapshot,
            Long workspaceRevisionId) {}
}
