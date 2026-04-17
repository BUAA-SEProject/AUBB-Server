package com.aubb.server.modules.assignment.application.judge;

import com.aubb.server.modules.assignment.domain.judge.AssignmentJudgeLanguage;
import java.util.List;

public record AssignmentJudgeConfigInput(
        AssignmentJudgeLanguage language,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        Integer outputLimitKb,
        List<AssignmentJudgeCaseInput> testCases) {}
