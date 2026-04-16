package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingExecutionEnvironmentView(
        Long profileId,
        String profileCode,
        String profileName,
        String profileScope,
        String languageVersion,
        String workingDirectory,
        Map<String, String> environmentVariables,
        Integer cpuRateLimit,
        String compileCommand,
        String runCommand,
        String initScript,
        List<ProgrammingSourceFile> supportFiles) {}
