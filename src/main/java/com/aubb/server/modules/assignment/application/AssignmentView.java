package com.aubb.server.modules.assignment.application;

import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import java.time.OffsetDateTime;

public record AssignmentView(
        Long id,
        Long offeringId,
        String offeringCode,
        String offeringName,
        AssignmentClassView teachingClass,
        String title,
        String description,
        AssignmentStatus status,
        OffsetDateTime openAt,
        OffsetDateTime dueAt,
        Integer maxSubmissions,
        OffsetDateTime publishedAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
