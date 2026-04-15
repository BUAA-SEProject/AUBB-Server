package com.aubb.server.modules.submission.application;

import com.aubb.server.modules.submission.domain.SubmissionStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record SubmissionView(
        Long id,
        String submissionNo,
        Long assignmentId,
        String assignmentTitle,
        Long offeringId,
        Long teachingClassId,
        Long submitterUserId,
        Integer attemptNo,
        SubmissionStatus status,
        String contentText,
        List<SubmissionArtifactView> artifacts,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
