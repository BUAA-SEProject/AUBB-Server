package com.aubb.server.modules.lab.application;

import com.aubb.server.modules.lab.domain.LabStatus;
import java.time.OffsetDateTime;

public record LabView(
        Long id,
        Long offeringId,
        LabClassView teachingClass,
        String title,
        String description,
        LabStatus status,
        OffsetDateTime publishedAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
