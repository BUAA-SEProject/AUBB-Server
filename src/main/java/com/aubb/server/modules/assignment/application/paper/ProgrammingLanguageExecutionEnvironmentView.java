package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingLanguageExecutionEnvironmentView(
        ProgrammingLanguage programmingLanguage, ProgrammingExecutionEnvironmentView executionEnvironment) {}
