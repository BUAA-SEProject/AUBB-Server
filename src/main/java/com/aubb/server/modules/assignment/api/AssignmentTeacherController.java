package com.aubb.server.modules.assignment.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.assignment.application.AssignmentApplicationService;
import com.aubb.server.modules.assignment.application.AssignmentView;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeCaseInput;
import com.aubb.server.modules.assignment.application.judge.AssignmentJudgeConfigInput;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.domain.judge.AssignmentJudgeLanguage;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
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
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class AssignmentTeacherController {

    private final AssignmentApplicationService assignmentApplicationService;

    @PostMapping("/course-offerings/{offeringId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AssignmentView create(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.createAssignment(
                offeringId,
                request.title(),
                request.description(),
                request.teachingClassId(),
                request.openAt(),
                request.dueAt(),
                request.maxSubmissions(),
                request.toJudgeConfigInput(),
                principal);
    }

    @GetMapping("/course-offerings/{offeringId}/assignments")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AssignmentView> list(
            @PathVariable Long offeringId,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.listTeacherAssignments(
                offeringId, status, teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/assignments/{assignmentId}")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView detail(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.getTeacherAssignment(assignmentId, principal);
    }

    @PostMapping("/assignments/{assignmentId}/publish")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView publish(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.publishAssignment(assignmentId, principal);
    }

    @PostMapping("/assignments/{assignmentId}/close")
    @PreAuthorize("isAuthenticated()")
    public AssignmentView close(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return assignmentApplicationService.closeAssignment(assignmentId, principal);
    }

    public record CreateAssignmentRequest(
            @NotBlank String title,
            String description,
            Long teachingClassId,
            @NotNull OffsetDateTime openAt,
            @NotNull OffsetDateTime dueAt,
            @NotNull @Positive Integer maxSubmissions,
            @Valid JudgeConfigRequest judgeConfig) {

        AssignmentJudgeConfigInput toJudgeConfigInput() {
            return judgeConfig == null ? null : judgeConfig.toInput();
        }
    }

    public record JudgeConfigRequest(
            @NotNull AssignmentJudgeLanguage language,
            @NotNull @Positive Integer timeLimitMs,
            @NotNull @Positive Integer memoryLimitMb,
            @NotNull @Positive Integer outputLimitKb,
            @NotEmpty List<@Valid JudgeCaseRequest> testCases) {

        AssignmentJudgeConfigInput toInput() {
            return new AssignmentJudgeConfigInput(
                    language,
                    timeLimitMs,
                    memoryLimitMb,
                    outputLimitKb,
                    testCases.stream().map(JudgeCaseRequest::toInput).toList());
        }
    }

    public record JudgeCaseRequest(
            @NotNull String stdinText,
            @NotNull String expectedStdout,
            @NotNull @PositiveOrZero Integer score) {

        AssignmentJudgeCaseInput toInput() {
            return new AssignmentJudgeCaseInput(stdinText, expectedStdout, score);
        }
    }
}
