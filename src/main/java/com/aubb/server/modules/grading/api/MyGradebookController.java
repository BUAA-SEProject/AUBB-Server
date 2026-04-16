package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.gradebook.GradebookApplicationService;
import com.aubb.server.modules.grading.application.gradebook.StudentGradebookView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MyGradebookController {

    private final GradebookApplicationService gradebookApplicationService;

    @GetMapping("/course-offerings/{offeringId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public StudentGradebookView getMyGradebook(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getMyGradebook(offeringId, principal);
    }
}
