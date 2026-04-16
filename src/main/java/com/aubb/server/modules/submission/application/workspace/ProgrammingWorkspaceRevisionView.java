package com.aubb.server.modules.submission.application.workspace;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.submission.application.SubmissionArtifactView;
import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceRevisionKind;
import java.time.OffsetDateTime;
import java.util.List;

public record ProgrammingWorkspaceRevisionView(
        Long id,
        Long revisionNo,
        ProgrammingWorkspaceRevisionKind revisionKind,
        String revisionMessage,
        Long assignmentId,
        Long assignmentQuestionId,
        ProgrammingLanguage programmingLanguage,
        String codeText,
        String entryFilePath,
        List<ProgrammingSourceFile> files,
        List<String> directories,
        List<Long> artifactIds,
        List<SubmissionArtifactView> artifacts,
        String lastStdinText,
        OffsetDateTime createdAt) {}
