package com.aubb.server.modules.judge.application.sample;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.judge.application.JudgeJobStoredReport;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.aubb.server.modules.judge.domain.ProgrammingSampleRunInputMode;
import com.aubb.server.modules.judge.domain.ProgrammingSampleRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingSampleRunView(
        Long id,
        Long assignmentId,
        Long assignmentQuestionId,
        ProgrammingLanguage programmingLanguage,
        String entryFilePath,
        List<ProgrammingSourceFile> files,
        List<String> directories,
        List<Long> artifactIds,
        Long workspaceRevisionId,
        ProgrammingSampleRunInputMode stdinMode,
        ProgrammingSampleRunStatus status,
        JudgeVerdict verdict,
        String stdinText,
        String expectedStdout,
        String stdoutText,
        String stderrText,
        String resultSummary,
        String errorMessage,
        Long timeMillis,
        Long memoryBytes,
        JudgeJobStoredReport detailReport,
        String detailReportUnavailableReasonCode,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt) {}
