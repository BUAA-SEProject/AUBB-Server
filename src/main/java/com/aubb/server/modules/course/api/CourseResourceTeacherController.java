package com.aubb.server.modules.course.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.course.application.CourseResourceApplicationService;
import com.aubb.server.modules.course.application.CourseResourceDownload;
import com.aubb.server.modules.course.application.view.CourseResourceView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class CourseResourceTeacherController {

    private final CourseResourceApplicationService courseResourceApplicationService;

    @PostMapping("/course-offerings/{offeringId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public CourseResourceView uploadResource(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam String title,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseResourceApplicationService.uploadResource(offeringId, teachingClassId, title, file, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/resources")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseResourceView> listResources(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return courseResourceApplicationService.listTeacherResources(
                offeringId, teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/course-resources/{resourceId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadResource(
            @PathVariable Long resourceId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(courseResourceApplicationService.downloadTeacherResource(resourceId, principal));
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
