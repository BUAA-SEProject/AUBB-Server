package com.aubb.server.modules.course.application;

public record CourseMemberBatchError(int row, Long userId, String username, String reason) {}
