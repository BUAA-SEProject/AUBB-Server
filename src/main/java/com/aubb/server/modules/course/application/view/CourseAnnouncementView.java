package com.aubb.server.modules.course.application.view;

import java.time.OffsetDateTime;

public record CourseAnnouncementView(
        Long id,
        Long offeringId,
        Long teachingClassId,
        String title,
        String body,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt) {}
