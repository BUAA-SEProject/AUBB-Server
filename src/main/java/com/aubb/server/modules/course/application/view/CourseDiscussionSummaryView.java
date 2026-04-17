package com.aubb.server.modules.course.application.view;

import java.time.OffsetDateTime;

public record CourseDiscussionSummaryView(
        Long id,
        Long offeringId,
        Long teachingClassId,
        String title,
        boolean locked,
        int replyCount,
        CourseDiscussionAuthorView author,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt) {}
