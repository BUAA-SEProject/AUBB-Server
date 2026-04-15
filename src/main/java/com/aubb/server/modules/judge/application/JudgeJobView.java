package com.aubb.server.modules.judge.application;

import com.aubb.server.modules.judge.domain.JudgeJobStatus;
import com.aubb.server.modules.judge.domain.JudgeTriggerType;
import java.time.OffsetDateTime;

public record JudgeJobView(
        Long id,
        Long submissionId,
        Long assignmentId,
        Long offeringId,
        Long teachingClassId,
        Long submitterUserId,
        Long requestedByUserId,
        JudgeTriggerType triggerType,
        JudgeJobStatus status,
        String engineCode,
        String engineJobRef,
        String resultSummary,
        OffsetDateTime queuedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
