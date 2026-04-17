package com.aubb.server.modules.judge.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record JudgeJobArtifactTraceView(
        @Schema(description = "归档模式：OBJECT_STORAGE 或 INLINE_DB_JSON")
        String storageMode,

        @Schema(description = "归档时间") OffsetDateTime archivedAt,
        @Schema(description = "评测任务编号") Long judgeJobId,
        @Schema(description = "正式提交编号") Long submissionId,
        @Schema(description = "分题答案编号") Long submissionAnswerId,
        @Schema(description = "作业编号") Long assignmentId,
        @Schema(description = "题目编号") Long assignmentQuestionId,
        @Schema(description = "归档包含的逻辑产物") List<String> includedArtifacts,
        @Schema(description = "详细报告对象摘要") JudgeArtifactTraceItemView detailReport,
        @Schema(description = "源码快照对象摘要") JudgeArtifactTraceItemView sourceSnapshot,
        @Schema(description = "归档清单对象摘要") JudgeArtifactTraceItemView artifactManifest) {

    public JudgeJobArtifactTraceView {
        includedArtifacts = includedArtifacts == null ? List.of() : List.copyOf(includedArtifacts);
    }
}
