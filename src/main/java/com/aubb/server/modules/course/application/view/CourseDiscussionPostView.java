package com.aubb.server.modules.course.application.view;

import java.time.OffsetDateTime;

public record CourseDiscussionPostView(
        Long id,
        Long discussionId,
        Long replyToPostId,
        String body,
        CourseDiscussionAuthorView author,
        OffsetDateTime createdAt) {}
