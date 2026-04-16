package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.AssignmentGradePublicationView;
import com.aubb.server.modules.grading.application.BatchGradeImportResultView;
import com.aubb.server.modules.grading.application.BatchManualGradeResultView;
import com.aubb.server.modules.grading.application.GradeImportTemplateContent;
import com.aubb.server.modules.grading.application.GradingApplicationService;
import com.aubb.server.modules.grading.application.ManualGradeResultView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher")
public class TeacherGradingController {

    private final GradingApplicationService gradingApplicationService;

    @PostMapping("/submissions/{submissionId}/answers/{answerId}/grade")
    @PreAuthorize("isAuthenticated()")
    public ManualGradeResultView gradeAnswer(
            @PathVariable Long submissionId,
            @PathVariable Long answerId,
            @RequestBody @Validated GradeAnswerRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.gradeAnswer(
                submissionId, answerId, request.score(), request.feedbackText(), principal);
    }

    @PostMapping("/assignments/{assignmentId}/grades/batch-adjust")
    @PreAuthorize("isAuthenticated()")
    public BatchManualGradeResultView batchAdjustAnswers(
            @PathVariable Long assignmentId,
            @RequestBody @Validated BatchGradeRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        List<GradingApplicationService.BatchGradeItem> adjustments = request.adjustments().stream()
                .map(item -> new GradingApplicationService.BatchGradeItem(
                        item.submissionId(), item.answerId(), item.score(), item.feedbackText()))
                .toList();
        return gradingApplicationService.batchGradeAnswers(assignmentId, adjustments, principal);
    }

    @GetMapping("/assignments/{assignmentId}/grades/import-template")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportBatchGradeTemplate(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(gradingApplicationService.exportBatchGradeTemplate(assignmentId, principal));
    }

    @PostMapping("/assignments/{assignmentId}/grades/import")
    @PreAuthorize("isAuthenticated()")
    public BatchGradeImportResultView importBatchGrades(
            @PathVariable Long assignmentId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.importBatchGrades(assignmentId, file, principal);
    }

    @PostMapping("/assignments/{assignmentId}/grades/publish")
    @PreAuthorize("isAuthenticated()")
    public AssignmentGradePublicationView publishAssignmentGrades(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.publishAssignmentGrades(assignmentId, principal);
    }

    private ResponseEntity<byte[]> toDownloadResponse(GradeImportTemplateContent exportContent) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exportContent.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportContent.contentType()))
                .header("Content-Disposition", disposition.toString())
                .contentLength(exportContent.content().length)
                .body(exportContent.content());
    }

    public record GradeAnswerRequest(
            @NotNull @Min(0) @Max(1000) Integer score, String feedbackText) {}

    public record BatchGradeRequest(
            @NotNull @Size(min = 1, max = 100) List<@Valid BatchGradeItemRequest> adjustments) {}

    public record BatchGradeItemRequest(
            @NotNull Long submissionId,
            @NotNull Long answerId,
            @NotNull @Min(0) @Max(1000) Integer score,
            String feedbackText) {}
}
