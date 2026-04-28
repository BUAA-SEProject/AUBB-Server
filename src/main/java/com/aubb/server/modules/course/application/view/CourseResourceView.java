package com.aubb.server.modules.course.application.view;

import java.time.OffsetDateTime;

public record CourseResourceView(
        Long id,
        Long offeringId,
        Long teachingClassId,
        String title,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        OffsetDateTime createdAt) {}
