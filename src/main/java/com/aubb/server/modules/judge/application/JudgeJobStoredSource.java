package com.aubb.server.modules.judge.application;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import java.util.List;

public record JudgeJobStoredSource(
        ProgrammingLanguage programmingLanguage,
        String entryFilePath,
        List<ProgrammingSourceFile> files,
        List<Long> artifactIds,
        Long submissionId,
        Long submissionAnswerId,
        Long assignmentQuestionId) {

    public JudgeJobStoredSource {
        files = files == null ? List.of() : List.copyOf(files);
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }
}
