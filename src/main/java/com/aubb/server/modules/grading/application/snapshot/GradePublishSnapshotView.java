package com.aubb.server.modules.grading.application.snapshot;

import java.time.OffsetDateTime;

public record GradePublishSnapshotView(
        Long snapshotId,
        Long studentUserId,
        String username,
        String displayName,
        Long teachingClassId,
        String teachingClassCode,
        String teachingClassName,
        Long submissionId,
        String submissionNo,
        Integer attemptNo,
        OffsetDateTime submittedAt,
        Integer totalFinalScore,
        Integer totalMaxScore,
        Integer autoScoredScore,
        Integer manualScoredScore,
        Boolean fullyGraded,
        GradePublishSnapshotPayloadView snapshot) {}
