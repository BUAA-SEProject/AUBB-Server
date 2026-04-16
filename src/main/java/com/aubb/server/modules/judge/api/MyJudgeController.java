package com.aubb.server.modules.judge.api;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.JudgeApplicationService;
import com.aubb.server.modules.judge.application.JudgeJobReportView;
import com.aubb.server.modules.judge.application.JudgeJobView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MyJudgeController {

    private final JudgeApplicationService judgeApplicationService;

    @GetMapping("/submissions/{submissionId}/judge-jobs")
    @PreAuthorize("isAuthenticated()")
    public List<JudgeJobView> listJudgeJobs(
            @PathVariable Long submissionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.listMyJudgeJobs(submissionId, principal);
    }

    @GetMapping("/submission-answers/{answerId}/judge-jobs")
    @PreAuthorize("isAuthenticated()")
    public List<JudgeJobView> listAnswerJudgeJobs(
            @PathVariable Long answerId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.listMyAnswerJudgeJobs(answerId, principal);
    }

    @GetMapping("/judge-jobs/{judgeJobId}/report")
    @PreAuthorize("isAuthenticated()")
    public JudgeJobReportView getJudgeJobReport(
            @PathVariable Long judgeJobId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.getMyJudgeJobReport(judgeJobId, principal);
    }
}
