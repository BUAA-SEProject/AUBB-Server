package com.aubb.server.modules.course.application.view;

import com.aubb.server.modules.course.domain.term.AcademicTermSemester;
import com.aubb.server.modules.course.domain.term.AcademicTermStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AcademicTermView(
        Long id,
        String termCode,
        String termName,
        String schoolYear,
        AcademicTermSemester semester,
        LocalDate startDate,
        LocalDate endDate,
        AcademicTermStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
