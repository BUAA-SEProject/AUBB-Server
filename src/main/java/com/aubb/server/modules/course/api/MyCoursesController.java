package com.aubb.server.modules.course.api;

import com.aubb.server.modules.course.application.CourseTeachingApplicationService;
import com.aubb.server.modules.course.application.view.MyCourseView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/courses")
@RequiredArgsConstructor
public class MyCoursesController {

    private final CourseTeachingApplicationService courseTeachingApplicationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MyCourseView> myCourses(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseTeachingApplicationService.listMyCourses(principal);
    }
}
