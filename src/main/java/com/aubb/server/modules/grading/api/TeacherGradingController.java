package com.aubb.server.modules.grading.api;

import com.aubb.server.modules.grading.application.AssignmentGradePublicationView;
import com.aubb.server.modules.grading.application.BatchGradeImportResultView;
import com.aubb.server.modules.grading.application.BatchManualGradeResultView;
import com.aubb.server.modules.grading.application.GradeImportTemplateContent;
import com.aubb.server.modules.grading.application.GradingApplicationService;
import com.aubb.server.modules.grading.application.ManualGradeResultView;
import com.aubb.server.modules.grading.application.snapshot.GradePublishBatchDetailView;
import com.aubb.server.modules.grading.application.snapshot.GradePublishBatchSummaryView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "教师或助教对分题答案执行人工批改")
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
    @Operation(summary = "教师按作业批量调整分题成绩")
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
    @Operation(summary = "教师导出作业批量调分模板")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportBatchGradeTemplate(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(gradingApplicationService.exportBatchGradeTemplate(assignmentId, principal));
    }

    @PostMapping("/assignments/{assignmentId}/grades/import")
    @Operation(summary = "教师导入作业批量调分结果")
    @PreAuthorize("isAuthenticated()")
    public BatchGradeImportResultView importBatchGrades(
            @PathVariable Long assignmentId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.importBatchGrades(assignmentId, file, principal);
    }

    @PostMapping("/assignments/{assignmentId}/grades/publish")
    @Operation(summary = "教师发布作业成绩并生成发布快照")
    @PreAuthorize("isAuthenticated()")
    public AssignmentGradePublicationView publishAssignmentGrades(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.publishAssignmentGrades(assignmentId, principal);
    }

    @GetMapping("/assignments/{assignmentId}/grade-publish-batches")
    @Operation(summary = "教师查看作业成绩发布快照批次列表")
    @PreAuthorize("isAuthenticated()")
    public List<GradePublishBatchSummaryView> listGradePublishBatches(
            @PathVariable Long assignmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.listGradePublishBatches(assignmentId, principal);
    }

    @GetMapping("/assignments/{assignmentId}/grade-publish-batches/{batchId}")
    @Operation(summary = "教师查看作业成绩发布快照批次详情")
    @PreAuthorize("isAuthenticated()")
    public GradePublishBatchDetailView getGradePublishBatch(
            @PathVariable Long assignmentId,
            @PathVariable Long batchId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return gradingApplicationService.getGradePublishBatch(assignmentId, batchId, principal);
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
