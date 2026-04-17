package com.aubb.server.modules.submission.application.workspace;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceRevisionKind;
import java.time.OffsetDateTime;

public record ProgrammingWorkspaceRevisionSummaryView(
        Long id,
        Long revisionNo,
        ProgrammingWorkspaceRevisionKind revisionKind,
        String revisionMessage,
        ProgrammingLanguage programmingLanguage,
        String entryFilePath,
        Integer sourceFileCount,
        Integer directoryCount,
        Integer artifactCount,
        OffsetDateTime createdAt) {}
