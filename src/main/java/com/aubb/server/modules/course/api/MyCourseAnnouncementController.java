package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.announcement.CourseAnnouncementApplicationService;
import com.aubb.server.modules.course.application.view.CourseAnnouncementView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MyCourseAnnouncementController {

    private final CourseAnnouncementApplicationService courseAnnouncementApplicationService;

    @GetMapping("/course-classes/{teachingClassId}/announcements")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseAnnouncementView> listAnnouncements(
            @PathVariable Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAnnouncementApplicationService.listMyAnnouncements(teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/announcements/{announcementId}")
    @PreAuthorize("isAuthenticated()")
    public CourseAnnouncementView getAnnouncement(
            @PathVariable Long announcementId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseAnnouncementApplicationService.getMyAnnouncement(announcementId, principal);
    }
}
