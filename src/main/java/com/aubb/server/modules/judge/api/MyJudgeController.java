package com.aubb.server.modules.judge.api;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.application.JudgeApplicationService;
import com.aubb.server.modules.judge.application.JudgeJobReportDownload;
import com.aubb.server.modules.judge.application.JudgeJobReportView;
import com.aubb.server.modules.judge.application.JudgeJobView;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MyJudgeController {

    private final JudgeApplicationService judgeApplicationService;

    @GetMapping("/submissions/{submissionId}/judge-jobs")
    @Operation(summary = "学生查看提交下的评测任务列表")
    @PreAuthorize("isAuthenticated()")
    public List<JudgeJobView> listJudgeJobs(
            @PathVariable Long submissionId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.listMyJudgeJobs(submissionId, principal);
    }

    @GetMapping("/submission-answers/{answerId}/judge-jobs")
    @Operation(summary = "学生查看分题答案下的评测任务列表")
    @PreAuthorize("isAuthenticated()")
    public List<JudgeJobView> listAnswerJudgeJobs(
            @PathVariable Long answerId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.listMyAnswerJudgeJobs(answerId, principal);
    }

    @GetMapping("/judge-jobs/{judgeJobId}/report")
    @Operation(summary = "学生查看评测详细报告")
    @PreAuthorize("isAuthenticated()")
    public JudgeJobReportView getJudgeJobReport(
            @PathVariable Long judgeJobId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return judgeApplicationService.getMyJudgeJobReport(judgeJobId, principal);
    }

    @GetMapping("/judge-jobs/{judgeJobId}/report/download")
    @Operation(summary = "学生下载评测详细报告")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadJudgeJobReport(
            @PathVariable Long judgeJobId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return toDownloadResponse(judgeApplicationService.downloadMyJudgeJobReport(judgeJobId, principal));
    }

    private ResponseEntity<byte[]> toDownloadResponse(JudgeJobReportDownload reportDownload) {
        MediaType mediaType = MediaType.parseMediaType(reportDownload.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(reportDownload.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", disposition.toString())
                .contentLength(reportDownload.content().length)
                .body(reportDownload.content());
    }
}
