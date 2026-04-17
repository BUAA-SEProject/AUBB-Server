package com.aubb.server.modules.judge.application.environment;

import com.aubb.server.modules.assignment.application.paper.ProgrammingExecutionEnvironmentView;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import java.time.OffsetDateTime;

public record JudgeEnvironmentProfileView(
        Long id,
        Long offeringId,
        String profileCode,
        String profileName,
        String description,
        ProgrammingLanguage programmingLanguage,
        ProgrammingExecutionEnvironmentView executionEnvironment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime archivedAt) {}
