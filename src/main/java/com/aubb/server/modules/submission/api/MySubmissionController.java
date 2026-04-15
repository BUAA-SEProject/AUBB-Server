package com.aubb.server.modules.submission.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.application.SubmissionApplicationService;
import com.aubb.server.modules.submission.application.SubmissionArtifactDownload;
import com.aubb.server.modules.submission.application.SubmissionArtifactView;
import com.aubb.server.modules.submission.application.SubmissionView;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MySubmissionController {

    private final SubmissionApplicationService submissionApplicationService;

    @PostMapping("/assignments/{assignmentId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public SubmissionView create(
            @PathVariable Long assignmentId,
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return submissionApplicationService.createSubmission(
                assignmentId, request.contentText(), request.artifactIds(), principal);
    }

    @PostMapping("/assignments/{assignmentId}/submission-artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public SubmissionArtifactView uploadArtifact(
            @PathVariable Long assignmentId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return submissionApplicationService.uploadArtifact(assignmentId, file, principal);
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<SubmissionView> listByAssignment(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return submissionApplicationService.listMySubmissions(assignmentId, page, pageSize, principal);
    }

    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public SubmissionView detail(
            @PathVariable Long submissionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return submissionApplicationService.getMySubmission(submissionId, principal);
    }

    @GetMapping("/submission-artifacts/{artifactId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadArtifact(
            @PathVariable Long artifactId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(submissionApplicationService.downloadMyArtifact(artifactId, principal));
    }

    private ResponseEntity<byte[]> toDownloadResponse(SubmissionArtifactDownload artifactDownload) {
        MediaType mediaType = MediaType.parseMediaType(artifactDownload.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(artifactDownload.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", disposition.toString())
                .contentLength(artifactDownload.content().length)
                .body(artifactDownload.content());
    }

    public record CreateSubmissionRequest(String contentText, List<Long> artifactIds) {}
}
