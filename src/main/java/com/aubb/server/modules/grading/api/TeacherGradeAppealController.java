package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.appeal.GradeAppealApplicationService;
import com.aubb.server.modules.grading.application.appeal.GradeAppealApplicationService.ReviewDecision;
import com.aubb.server.modules.grading.application.appeal.GradeAppealView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher")
public class TeacherGradeAppealController {

    private final GradeAppealApplicationService gradeAppealApplicationService;

    @GetMapping("/assignments/{assignmentId}/grade-appeals")
    @PreAuthorize("isAuthenticated()")
    public List<GradeAppealView> listAssignmentAppeals(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradeAppealApplicationService.listAssignmentAppeals(assignmentId, status, principal);
    }

    @PostMapping("/grade-appeals/{appealId}/review")
    @PreAuthorize("isAuthenticated()")
    public GradeAppealView reviewAppeal(
            @PathVariable Long appealId,
            @RequestBody @Validated ReviewGradeAppealRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradeAppealApplicationService.reviewAppeal(
                appealId,
                request.decision(),
                request.responseText(),
                request.revisedScore(),
                request.revisedFeedbackText(),
                principal);
    }

    public record ReviewGradeAppealRequest(
            @NotNull ReviewDecision decision,
            @Size(max = 2000) String responseText,
            Integer revisedScore,
            @Size(max = 2000) String revisedFeedbackText) {}
}
