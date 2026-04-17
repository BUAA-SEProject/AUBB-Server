package com.aubb.server.modules.judge.application;

public record StoredJudgeArtifact(
        String objectKey, String contentType, long sizeBytes, String sha256Hex, boolean storedInObjectStorage) {}
