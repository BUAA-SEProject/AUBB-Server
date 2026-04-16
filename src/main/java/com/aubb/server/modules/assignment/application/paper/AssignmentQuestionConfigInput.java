package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingJudgeMode;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import java.util.List;

public record AssignmentQuestionConfigInput(
        List<ProgrammingLanguage> supportedLanguages,
        Integer maxFileCount,
        Integer maxFileSizeMb,
        List<String> acceptedExtensions,
        Boolean allowMultipleFiles,
        Boolean allowSampleRun,
        String sampleStdinText,
        String sampleExpectedStdout,
        String templateEntryFilePath,
        List<String> templateDirectories,
        List<ProgrammingSourceFile> templateFiles,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        Integer outputLimitKb,
        List<String> compileArgs,
        List<String> runArgs,
        ProgrammingJudgeMode judgeMode,
        String customJudgeScript,
        String referenceAnswer,
        List<ProgrammingJudgeCaseInput> judgeCases) {}
