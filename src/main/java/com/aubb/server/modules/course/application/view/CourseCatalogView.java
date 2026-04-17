package com.aubb.server.modules.course.application.view;

import com.aubb.server.modules.course.domain.catalog.CourseCatalogStatus;
import com.aubb.server.modules.course.domain.catalog.CourseType;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CourseCatalogView(
        Long id,
        String courseCode,
        String courseName,
        CourseType courseType,
        BigDecimal credit,
        Integer totalHours,
        OrgUnitSummaryView department,
        String description,
        CourseCatalogStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
