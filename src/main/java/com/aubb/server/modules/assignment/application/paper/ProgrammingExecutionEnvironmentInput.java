package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import java.util.List;
import java.util.Map;

public record ProgrammingExecutionEnvironmentInput(
        Long profileId,
        String profileCode,
        String profileName,
        String profileScope,
        String languageVersion,
        String workingDirectory,
        String initScript,
        String compileCommand,
        String runCommand,
        Map<String, String> environmentVariables,
        Integer cpuRateLimit,
        List<ProgrammingSourceFile> supportFiles) {}
