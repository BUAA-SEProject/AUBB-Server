package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.CourseCatalogStatus;
import com.aubb.server.modules.course.domain.CourseType;
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
