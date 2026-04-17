package com.aubb.server.modules.course.application.view;

import java.time.OffsetDateTime;
import java.util.List;

public record CourseDiscussionDetailView(
        Long id,
        Long offeringId,
        Long teachingClassId,
        String title,
        boolean locked,
        CourseDiscussionAuthorView author,
        int replyCount,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt,
        List<CourseDiscussionPostView> posts) {}
