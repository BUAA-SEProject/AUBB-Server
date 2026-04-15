package com.aubb.server.modules.judge.api;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.JudgeApplicationService;
import com.aubb.server.modules.judge.application.JudgeJobView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class TeacherJudgeController {

    private final JudgeApplicationService judgeApplicationService;

    @GetMapping("/submissions/{submissionId}/judge-jobs")
    @PreAuthorize("isAuthenticated()")
    public List<JudgeJobView> listJudgeJobs(
            @PathVariable Long submissionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.listTeacherJudgeJobs(submissionId, principal);
    }

    @PostMapping("/submissions/{submissionId}/judge-jobs/requeue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public JudgeJobView requeueJudge(
            @PathVariable Long submissionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.requeueJudge(submissionId, principal);
    }
}
