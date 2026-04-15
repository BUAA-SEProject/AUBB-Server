package com.aubb.server.modules.submission.application;

public record SubmissionArtifactDownload(String originalFilename, String contentType, byte[] content) {}
