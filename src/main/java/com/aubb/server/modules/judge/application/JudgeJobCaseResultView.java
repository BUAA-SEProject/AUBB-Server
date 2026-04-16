package com.aubb.server.modules.judge.application;

import com.aubb.server.modules.judge.domain.JudgeVerdict;

public record JudgeJobCaseResultView(
        Integer caseOrder,
        JudgeVerdict verdict,
        Integer score,
        Integer maxScore,
        String stdoutExcerpt,
        String stderrExcerpt,
        Long timeMillis,
        Long memoryBytes,
        String errorMessage) {}
