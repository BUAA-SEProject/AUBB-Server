package com.aubb.server.modules.submission.application.workspace;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.submission.application.SubmissionArtifactView;
import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceRevisionKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingWorkspaceView(
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
        Long latestRevisionId,
        Long latestRevisionNo,
        ProgrammingWorkspaceRevisionKind latestRevisionKind,
        Boolean editable,
        String editBlockedReasonCode,
        Boolean runnable,
        String runBlockedReasonCode,
        OffsetDateTime updatedAt) {}
