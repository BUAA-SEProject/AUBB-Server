package com.aubb.server.modules.judge.application;

import com.aubb.server.modules.judge.domain.JudgeJobStatus;
import com.aubb.server.modules.judge.domain.JudgeTriggerType;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import java.time.OffsetDateTime;

public record JudgeJobView(
        Long id,
        Long submissionId,
        Long submissionAnswerId,
        Long assignmentId,
        Long assignmentQuestionId,
        Long offeringId,
        Long teachingClassId,
        Long submitterUserId,
        Long requestedByUserId,
        JudgeTriggerType triggerType,
        JudgeJobStatus status,
        String engineCode,
        String engineJobRef,
        String resultSummary,
        JudgeVerdict verdict,
        Integer totalCaseCount,
        Integer passedCaseCount,
        Integer score,
        Integer maxScore,
        String stdoutExcerpt,
        String stderrExcerpt,
        Long timeMillis,
        Long memoryBytes,
        String errorMessage,
        java.util.List<JudgeJobCaseResultView> caseResults,
        boolean detailReportAvailable,
        boolean artifactTraceAvailable,
        OffsetDateTime queuedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
