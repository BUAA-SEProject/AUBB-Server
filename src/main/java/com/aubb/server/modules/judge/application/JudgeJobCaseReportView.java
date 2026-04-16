package com.aubb.server.modules.judge.application;

import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeJobCaseReportView(
        Integer caseOrder,
        JudgeVerdict verdict,
        Integer score,
        Integer maxScore,
        String stdinText,
        String expectedStdout,
        String stdoutText,
        String stderrText,
        Long timeMillis,
        Long memoryBytes,
        String errorMessage,
        String engineStatus,
        Integer exitStatus,
        List<String> compileCommand,
        List<String> runCommand) {}
