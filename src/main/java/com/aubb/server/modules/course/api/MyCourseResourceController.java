package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseResourceApplicationService;
import com.aubb.server.modules.course.application.CourseResourceDownload;
import com.aubb.server.modules.course.application.view.CourseResourceView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
public class MyCourseResourceController {

    private final CourseResourceApplicationService courseResourceApplicationService;

    @GetMapping("/course-classes/{teachingClassId}/resources")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseResourceView> listResources(
            @PathVariable Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseResourceApplicationService.listMyResources(teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/course-resources/{resourceId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadResource(
            @PathVariable Long resourceId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(courseResourceApplicationService.downloadMyResource(resourceId, principal));
    }

    private ResponseEntity<byte[]> toDownloadResponse(CourseResourceDownload resourceDownload) {
        MediaType mediaType = MediaType.parseMediaType(resourceDownload.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(resourceDownload.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", disposition.toString())
                .contentLength(resourceDownload.content().length)
                .body(resourceDownload.content());
    }
}
