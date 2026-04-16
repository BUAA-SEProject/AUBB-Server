package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.appeal.GradeAppealApplicationService;
import com.aubb.server.modules.grading.application.appeal.GradeAppealView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MyGradeAppealController {

    private final GradeAppealApplicationService gradeAppealApplicationService;

    @PostMapping("/submissions/{submissionId}/answers/{answerId}/appeals")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public GradeAppealView createAppeal(
            @PathVariable Long submissionId,
            @PathVariable Long answerId,
            @RequestBody @Validated CreateGradeAppealRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradeAppealApplicationService.createAppeal(submissionId, answerId, request.reason(), principal);
    }

    @GetMapping("/course-offerings/{offeringId}/grade-appeals")
    @PreAuthorize("isAuthenticated()")
    public List<GradeAppealView> listMyAppeals(
            @PathVariable Long offeringId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradeAppealApplicationService.listMyAppeals(offeringId, status, principal);
    }

    public record CreateGradeAppealRequest(
            @NotBlank @Size(max = 2000) String reason) {}
}
