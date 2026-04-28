package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.discussion.CourseDiscussionApplicationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MyCourseDiscussionController {

    private final CourseDiscussionApplicationService courseDiscussionApplicationService;

    @PostMapping("/course-classes/{teachingClassId}/discussions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionSummaryView createDiscussion(
            @PathVariable Long teachingClassId,
            @Valid @RequestBody CreateDiscussionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.createMyDiscussion(
                teachingClassId, request.title(), request.body(), principal);
    }

    @GetMapping("/course-classes/{teachingClassId}/discussions")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseDiscussionSummaryView> listDiscussions(
            @PathVariable Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.listMyDiscussions(teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/discussions/{discussionId}")
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionDetailView getDiscussion(
            @PathVariable Long discussionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.getMyDiscussion(discussionId, principal);
    }

    @PostMapping("/discussions/{discussionId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CourseDiscussionPostView reply(
            @PathVariable Long discussionId,
            @Valid @RequestBody ReplyRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseDiscussionApplicationService.replyAsMy(
                discussionId, request.replyToPostId(), request.body(), principal);
    }

    public record CreateDiscussionRequest(
            @NotBlank String title, @NotBlank String body) {}

    public record ReplyRequest(Long replyToPostId, @NotBlank String body) {}
}
