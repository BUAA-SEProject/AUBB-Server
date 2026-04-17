package com.aubb.server.modules.grading.application.snapshot;

import java.time.OffsetDateTime;

public record GradePublishBatchSummaryView(
        Long batchId,
        Long assignmentId,
        Long offeringId,
        Long teachingClassId,
        Integer publishSequence,
        Integer snapshotCount,
        Boolean initialPublication,
        Long publishedByUserId,
        OffsetDateTime publishedAt) {}
