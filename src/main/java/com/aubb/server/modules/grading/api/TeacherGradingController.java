package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.AssignmentGradePublicationView;
import com.aubb.server.modules.grading.application.GradingApplicationService;
import com.aubb.server.modules.grading.application.ManualGradeResultView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher")
public class TeacherGradingController {

    private final GradingApplicationService gradingApplicationService;

    @PostMapping("/submissions/{submissionId}/answers/{answerId}/grade")
    @PreAuthorize("isAuthenticated()")
    public ManualGradeResultView gradeAnswer(
            @PathVariable Long submissionId,
            @PathVariable Long answerId,
            @RequestBody @Validated GradeAnswerRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.gradeAnswer(
                submissionId, answerId, request.score(), request.feedbackText(), principal);
    }

    @PostMapping("/assignments/{assignmentId}/grades/publish")
    @PreAuthorize("isAuthenticated()")
    public AssignmentGradePublicationView publishAssignmentGrades(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.publishAssignmentGrades(assignmentId, principal);
    }

    public record GradeAnswerRequest(
            @NotNull @Min(0) @Max(1000) Integer score, String feedbackText) {}
}
