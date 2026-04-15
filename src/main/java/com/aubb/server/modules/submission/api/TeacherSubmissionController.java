package com.aubb.server.modules.submission.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.application.SubmissionApplicationService;
import com.aubb.server.modules.submission.application.SubmissionView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class TeacherSubmissionController {

    private final SubmissionApplicationService submissionApplicationService;

    @GetMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<SubmissionView> listByAssignment(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) Long submitterUserId,
            @RequestParam(defaultValue = "false") boolean latestOnly,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return submissionApplicationService.listTeacherSubmissions(
                assignmentId, submitterUserId, latestOnly, page, pageSize, principal);
    }

    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public SubmissionView detail(
            @PathVariable Long submissionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return submissionApplicationService.getTeacherSubmission(submissionId, principal);
    }
}
