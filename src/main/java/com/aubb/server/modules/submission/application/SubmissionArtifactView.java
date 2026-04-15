package com.aubb.server.modules.submission.application;

import java.time.OffsetDateTime;

public record SubmissionArtifactView(
        Long id,
        Long assignmentId,
        Long submissionId,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        OffsetDateTime uploadedAt) {}
