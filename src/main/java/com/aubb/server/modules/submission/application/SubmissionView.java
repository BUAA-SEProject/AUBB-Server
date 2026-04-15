package com.aubb.server.modules.submission.application;

import com.aubb.server.modules.submission.domain.SubmissionStatus;
import java.time.OffsetDateTime;

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
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
