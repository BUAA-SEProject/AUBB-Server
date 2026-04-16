package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.gradebook.GradebookApplicationService;
import com.aubb.server.modules.grading.application.gradebook.GradebookPageView;
import com.aubb.server.modules.grading.application.gradebook.StudentGradebookView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher")
public class TeacherGradebookController {

    private final GradebookApplicationService gradebookApplicationService;

    @GetMapping("/course-offerings/{offeringId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public GradebookPageView getOfferingGradebook(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @RequestParam(defaultValue = "1") @Positive long page,
            @RequestParam(defaultValue = "20") @Positive long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getOfferingGradebook(
                offeringId, teachingClassId, studentUserId, page, pageSize, principal);
    }

    @GetMapping("/teaching-classes/{teachingClassId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public GradebookPageView getTeachingClassGradebook(
            @PathVariable Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @RequestParam(defaultValue = "1") @Positive long page,
            @RequestParam(defaultValue = "20") @Positive long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getTeachingClassGradebook(
                teachingClassId, studentUserId, page, pageSize, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/students/{studentUserId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public StudentGradebookView getStudentGradebook(
            @PathVariable Long offeringId,
            @PathVariable Long studentUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getStudentGradebook(offeringId, studentUserId, principal);
    }
}
