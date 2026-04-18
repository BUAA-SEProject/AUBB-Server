package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.announcement.CourseAnnouncementApplicationService;
import com.aubb.server.modules.course.application.view.CourseAnnouncementView;
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
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class CourseAnnouncementTeacherController {

    private final CourseAnnouncementApplicationService courseAnnouncementApplicationService;

    @PostMapping("/course-offerings/{offeringId}/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CourseAnnouncementView createAnnouncement(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAnnouncementApplicationService.createAnnouncement(
                offeringId, request.teachingClassId(), request.title(), request.body(), principal);
    }

    @GetMapping("/course-offerings/{offeringId}/announcements")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseAnnouncementView> listAnnouncements(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAnnouncementApplicationService.listTeacherAnnouncements(
                offeringId, teachingClassId, page, pageSize, principal);
    }

    public record CreateAnnouncementRequest(
            Long teachingClassId,
            @NotBlank String title,
            @NotBlank String body) {}
}
