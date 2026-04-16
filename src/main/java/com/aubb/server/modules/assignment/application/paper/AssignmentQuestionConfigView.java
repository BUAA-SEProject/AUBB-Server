package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentQuestionConfigView(
        List<ProgrammingLanguage> supportedLanguages,
        Integer maxFileCount,
        Integer maxFileSizeMb,
        List<String> acceptedExtensions,
        Boolean allowMultipleFiles,
        Boolean allowSampleRun,
        String sampleStdinText,
        String sampleExpectedStdout,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        Integer outputLimitKb,
        Integer judgeCaseCount,
        ProgrammingJudgeMode judgeMode,
        String customJudgeScript,
        String referenceAnswer,
        List<ProgrammingJudgeCaseView> judgeCases) {}
