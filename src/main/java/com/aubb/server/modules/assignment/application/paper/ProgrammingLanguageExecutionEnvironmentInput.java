package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;

public record ProgrammingLanguageExecutionEnvironmentInput(
        ProgrammingLanguage programmingLanguage, ProgrammingExecutionEnvironmentInput executionEnvironment) {}
