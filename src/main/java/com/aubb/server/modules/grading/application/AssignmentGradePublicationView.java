package com.aubb.server.modules.grading.application;

import java.time.OffsetDateTime;

public record AssignmentGradePublicationView(
        Long assignmentId,
        Long publishedByUserId,
        OffsetDateTime publishedAt,
        Long snapshotBatchId,
        Integer snapshotPublishSequence,
        Long snapshotCapturedByUserId,
        OffsetDateTime snapshotCapturedAt,
        Integer snapshotCount,
        Boolean initialPublication) {}
