package com.aubb.server.modules.lab.api;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.lab.application.LabApplicationService;
import com.aubb.server.modules.lab.application.LabReportAttachmentDownload;
import com.aubb.server.modules.lab.application.LabReportAttachmentView;
import com.aubb.server.modules.lab.application.LabReportView;
import com.aubb.server.modules.lab.application.LabView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PutMapping;
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
@Tag(name = "My Labs")
public class MyLabController {

    private final LabApplicationService labApplicationService;

    @GetMapping("/course-classes/{teachingClassId}/labs")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生按教学班查看实验列表")
    public PageResponse<LabView> list(
            @PathVariable Long teachingClassId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.listMyLabs(teachingClassId, page, pageSize, principal);
    }

    @GetMapping("/labs/{labId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生查看实验详情")
    public LabView detail(@PathVariable Long labId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.getMyLab(labId, principal);
    }

    @PostMapping("/labs/{labId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生上传实验报告附件")
    public LabReportAttachmentView uploadAttachment(
            @PathVariable Long labId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.uploadAttachment(labId, file, principal);
    }

    @PutMapping("/labs/{labId}/report")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生保存或提交实验报告")
    public LabReportView saveReport(
            @PathVariable Long labId,
            @Valid @RequestBody SaveLabReportRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.saveMyReport(
                labId, request.reportContentText(), request.attachmentIds(), request.submit(), principal);
    }

    @GetMapping("/labs/{labId}/report")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生查看自己的实验报告与评语")
    public LabReportView myReport(
            @PathVariable Long labId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return labApplicationService.getMyReport(labId, principal);
    }

    @GetMapping("/lab-report-attachments/{attachmentId}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生下载自己的实验报告附件")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long attachmentId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(labApplicationService.downloadMyAttachment(attachmentId, principal));
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

    public record SaveLabReportRequest(
            @Schema(description = "实验报告正文") String reportContentText,
            @Schema(description = "实验报告附件编号列表") List<Long> attachmentIds,
            @Schema(description = "是否直接提交") boolean submit) {}
}
