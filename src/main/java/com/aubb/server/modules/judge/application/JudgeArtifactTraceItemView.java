package com.aubb.server.modules.judge.application;

import io.swagger.v3.oas.annotations.media.Schema;

public record JudgeArtifactTraceItemView(
        @Schema(description = "是否已成功落到对象存储") boolean storedInObjectStorage,
        @Schema(description = "产物内容类型") String contentType,
        @Schema(description = "产物字节数") Long sizeBytes,
        @Schema(description = "产物 SHA-256 摘要") String sha256Hex) {}
