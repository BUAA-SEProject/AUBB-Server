package com.aubb.server.modules.judge.application;

import com.aubb.server.modules.judge.domain.JudgeJobStatus;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeJobReportView(
        Long id,
        Long submissionId,
        Long submissionAnswerId,
        Long assignmentId,
        Long assignmentQuestionId,
        JudgeJobStatus status,
        JudgeVerdict verdict,
        String resultSummary,
        String errorMessage,
        String stdoutText,
        String stderrText,
        Integer score,
        Integer maxScore,
        Integer passedCaseCount,
        Integer totalCaseCount,
        Long timeMillis,
        Long memoryBytes,
        Map<String, Object> executionMetadata,
        List<JudgeJobCaseReportView> caseReports,
        OffsetDateTime queuedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt) {}
