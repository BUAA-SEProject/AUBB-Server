package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseDiscussionApplicationService;
import com.aubb.server.modules.course.application.view.CourseDiscussionDetailView;
import com.aubb.server.modules.course.application.view.CourseDiscussionPostView;
import com.aubb.server.modules.course.application.view.CourseDiscussionSummaryView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class CourseDiscussionTeacherController {

    private final CourseDiscussionApplicationService courseDiscussionApplicationService;

    @PostMapping("/course-offerings/{offeringId}/discussions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionSummaryView createDiscussion(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateDiscussionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.createTeacherDiscussion(
                offeringId, request.teachingClassId(), request.title(), request.body(), principal);
    }

    @GetMapping("/course-offerings/{offeringId}/discussions")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseDiscussionSummaryView> listDiscussions(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.listTeacherDiscussions(
                offeringId, teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/discussions/{discussionId}")
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionDetailView getDiscussion(
            @PathVariable Long discussionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.getTeacherDiscussion(discussionId, principal);
    }

    @PostMapping("/discussions/{discussionId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionPostView reply(
            @PathVariable Long discussionId,
            @Valid @RequestBody ReplyRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.replyAsTeacher(
                discussionId, request.replyToPostId(), request.body(), principal);
    }

    @PutMapping("/discussions/{discussionId}/lock-state")
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionSummaryView updateLockState(
            @PathVariable Long discussionId,
            @Valid @RequestBody UpdateLockStateRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.updateTeacherLockState(discussionId, request.locked(), principal);
    }

    public record CreateDiscussionRequest(
            Long teachingClassId,
            @NotBlank String title,
            @NotBlank String body) {}

    public record ReplyRequest(Long replyToPostId, @NotBlank String body) {}

    public record UpdateLockStateRequest(boolean locked) {}
}
