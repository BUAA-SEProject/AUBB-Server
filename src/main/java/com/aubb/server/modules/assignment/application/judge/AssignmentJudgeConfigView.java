package com.aubb.server.modules.assignment.application.judge;

import com.aubb.server.modules.assignment.domain.judge.AssignmentJudgeLanguage;

public record AssignmentJudgeConfigView(
        boolean enabled,
        AssignmentJudgeLanguage language,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        Integer outputLimitKb,
        Integer caseCount) {}
