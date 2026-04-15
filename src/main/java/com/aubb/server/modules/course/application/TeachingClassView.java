package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.TeachingClassStatus;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import java.time.OffsetDateTime;

public record TeachingClassView(
        Long id,
        Long offeringId,
        String classCode,
        String className,
        Integer entryYear,
        OrgUnitSummaryView orgClass,
        Integer capacity,
        TeachingClassStatus status,
        String scheduleSummary,
        TeachingClassFeaturesView features,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
