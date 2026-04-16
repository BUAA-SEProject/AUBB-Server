package com.aubb.server.modules.submission.application.workspace;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.submission.application.SubmissionArtifactView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingWorkspaceView(
        Long assignmentId,
        Long assignmentQuestionId,
        ProgrammingLanguage programmingLanguage,
        String codeText,
        List<Long> artifactIds,
        List<SubmissionArtifactView> artifacts,
        OffsetDateTime updatedAt) {}
