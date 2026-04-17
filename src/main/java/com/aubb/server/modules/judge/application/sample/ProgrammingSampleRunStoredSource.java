package com.aubb.server.modules.judge.application.sample;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingSampleRunStoredSource(
        ProgrammingLanguage programmingLanguage,
        String entryFilePath,
        List<ProgrammingSourceFile> files,
        List<String> directories,
        List<Long> artifactIds,
        Long workspaceRevisionId) {

    public ProgrammingSampleRunStoredSource {
        files = files == null ? List.of() : List.copyOf(files);
        directories = directories == null ? List.of() : List.copyOf(directories);
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }
}
