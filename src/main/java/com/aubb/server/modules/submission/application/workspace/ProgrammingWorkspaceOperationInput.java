package com.aubb.server.modules.submission.application.workspace;

import com.aubb.server.modules.submission.domain.workspace.ProgrammingWorkspaceOperationType;

public record ProgrammingWorkspaceOperationInput(
        ProgrammingWorkspaceOperationType type, String path, String newPath, String content) {}
