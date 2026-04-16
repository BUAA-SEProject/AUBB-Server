package com.aubb.server.modules.grading.application;

import java.time.OffsetDateTime;

public record AssignmentGradePublicationView(Long assignmentId, Long publishedByUserId, OffsetDateTime publishedAt) {}
