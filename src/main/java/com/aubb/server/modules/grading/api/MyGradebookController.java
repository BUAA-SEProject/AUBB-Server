package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.gradebook.GradebookApplicationService;
import com.aubb.server.modules.grading.application.gradebook.GradebookExportContent;
import com.aubb.server.modules.grading.application.gradebook.StudentGradebookView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MyGradebookController {

    private final GradebookApplicationService gradebookApplicationService;

    @GetMapping("/course-offerings/{offeringId}/gradebook")
    @PreAuthorize("isAuthenticated()")
    public StudentGradebookView getMyGradebook(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradebookApplicationService.getMyGradebook(offeringId, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/gradebook/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportMyGradebook(
            @PathVariable Long offeringId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(gradebookApplicationService.exportMyGradebook(offeringId, principal));
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
