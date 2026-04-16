package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.gradebook.GradebookApplicationService;
import com.aubb.server.modules.grading.application.gradebook.GradebookExportContent;
import com.aubb.server.modules.grading.application.gradebook.GradebookPageView;
import com.aubb.server.modules.grading.application.gradebook.GradebookReportView;
import com.aubb.server.modules.grading.application.gradebook.StudentGradebookView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher")
public class TeacherGradebookController {

    private final GradebookApplicationService gradebookApplicationService;

    @GetMapping("/course-offerings/{offeringId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public GradebookPageView getOfferingGradebook(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @RequestParam(defaultValue = "1") @Positive long page,
            @RequestParam(defaultValue = "20") @Positive long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getOfferingGradebook(
                offeringId, teachingClassId, studentUserId, page, pageSize, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/gradebook/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportOfferingGradebook(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(gradebookApplicationService.exportOfferingGradebook(
                offeringId, teachingClassId, studentUserId, principal));
    }

    @GetMapping("/course-offerings/{offeringId}/gradebook/report")
    @PreAuthorize("isAuthenticated()")
    public GradebookReportView getOfferingGradebookReport(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getOfferingGradebookReport(
                offeringId, teachingClassId, studentUserId, principal);
    }

    @GetMapping("/teaching-classes/{teachingClassId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public GradebookPageView getTeachingClassGradebook(
            @PathVariable Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @RequestParam(defaultValue = "1") @Positive long page,
            @RequestParam(defaultValue = "20") @Positive long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getTeachingClassGradebook(
                teachingClassId, studentUserId, page, pageSize, principal);
    }

    @GetMapping("/teaching-classes/{teachingClassId}/gradebook/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportTeachingClassGradebook(
            @PathVariable Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(
                gradebookApplicationService.exportTeachingClassGradebook(teachingClassId, studentUserId, principal));
    }

    @GetMapping("/teaching-classes/{teachingClassId}/gradebook/report")
    @PreAuthorize("isAuthenticated()")
    public GradebookReportView getTeachingClassGradebookReport(
            @PathVariable Long teachingClassId,
            @RequestParam(required = false) Long studentUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getTeachingClassGradebookReport(teachingClassId, studentUserId, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/students/{studentUserId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public StudentGradebookView getStudentGradebook(
            @PathVariable Long offeringId,
            @PathVariable Long studentUserId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getStudentGradebook(offeringId, studentUserId, principal);
    }

    private ResponseEntity<byte[]> toDownloadResponse(GradebookExportContent exportContent) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exportContent.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportContent.contentType()))
                .header("Content-Disposition", disposition.toString())
                .contentLength(exportContent.content().length)
                .body(exportContent.content());
    }
}
