package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.programming.ProgrammingSourceFile;
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
        String templateEntryFilePath,
        List<String> templateDirectories,
        List<ProgrammingSourceFile> templateFiles,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        Integer outputLimitKb,
        List<String> compileArgs,
        List<String> runArgs,
        Integer judgeCaseCount,
        ProgrammingJudgeMode judgeMode,
        String customJudgeScript,
        String referenceAnswer,
        List<ProgrammingJudgeCaseView> judgeCases,
        List<ProgrammingLanguageExecutionEnvironmentView> languageExecutionEnvironments,
        ProgrammingExecutionEnvironmentView executionEnvironment) {}
