package com.aubb.server.modules.lab.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.lab.application.LabApplicationService;
import com.aubb.server.modules.lab.application.LabReportAttachmentDownload;
import com.aubb.server.modules.lab.application.LabReportSummaryView;
import com.aubb.server.modules.lab.application.LabReportView;
import com.aubb.server.modules.lab.application.LabView;
import com.aubb.server.modules.lab.domain.LabReportStatus;
import com.aubb.server.modules.lab.domain.LabStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
@Tag(name = "Labs")
public class LabTeacherController {

    private final LabApplicationService labApplicationService;

    @PostMapping("/course-offerings/{offeringId}/labs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师创建实验草稿")
    public LabView create(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateLabRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.createLab(
                offeringId, request.teachingClassId(), request.title(), request.description(), principal);
    }

    @PutMapping("/labs/{labId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师更新实验草稿")
    public LabView update(
            @PathVariable Long labId,
            @Valid @RequestBody UpdateLabRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.updateLab(labId, request.title(), request.description(), principal);
    }

    @PostMapping("/labs/{labId}/publish")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师发布实验")
    public LabView publish(@PathVariable Long labId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.publishLab(labId, principal);
    }

    @PostMapping("/labs/{labId}/close")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师关闭实验")
    public LabView close(@PathVariable Long labId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.closeLab(labId, principal);
    }

    @GetMapping("/course-offerings/{offeringId}/labs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师分页查看实验列表")
    public PageResponse<LabView> list(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) LabStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.listTeacherLabs(offeringId, teachingClassId, status, page, pageSize, principal);
    }

    @GetMapping("/labs/{labId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师查看实验详情")
    public LabView detail(@PathVariable Long labId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.getTeacherLab(labId, principal);
    }

    @GetMapping("/labs/{labId}/reports")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师分页查看实验报告列表")
    public PageResponse<LabReportSummaryView> listReports(
            @PathVariable Long labId,
            @RequestParam(required = false) LabReportStatus status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.listTeacherReports(labId, status, page, pageSize, principal);
    }

    @GetMapping("/lab-reports/{reportId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师查看单份实验报告详情")
    public LabReportView reportDetail(
            @PathVariable Long reportId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.getTeacherReport(reportId, principal);
    }

    @PutMapping("/lab-reports/{reportId}/review")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师填写实验报告批注与评语")
    public LabReportView review(
            @PathVariable Long reportId,
            @Valid @RequestBody ReviewLabReportRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.reviewReport(
                reportId, request.teacherAnnotationText(), request.teacherCommentText(), principal);
    }

    @PostMapping("/lab-reports/{reportId}/publish")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师发布实验报告评阅结果")
    public LabReportView publishReview(
            @PathVariable Long reportId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.publishReport(reportId, principal);
    }

    @GetMapping("/lab-report-attachments/{attachmentId}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "教师下载实验报告附件")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long attachmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(labApplicationService.downloadTeacherAttachment(attachmentId, principal));
    }

    private ResponseEntity<byte[]> toDownloadResponse(LabReportAttachmentDownload attachmentDownload) {
        MediaType mediaType = MediaType.parseMediaType(attachmentDownload.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachmentDownload.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", disposition.toString())
                .contentLength(attachmentDownload.content().length)
                .body(attachmentDownload.content());
    }

    public record CreateLabRequest(
            @Schema(description = "所属教学班") @NotNull Long teachingClassId,
            @Schema(description = "实验标题") @NotBlank String title,
            @Schema(description = "实验说明") String description) {}

    public record UpdateLabRequest(
            @Schema(description = "实验标题") @NotBlank String title,
            @Schema(description = "实验说明") String description) {}

    public record ReviewLabReportRequest(
            @Schema(description = "教师批注") String teacherAnnotationText,
            @Schema(description = "教师评语") String teacherCommentText) {}
}
