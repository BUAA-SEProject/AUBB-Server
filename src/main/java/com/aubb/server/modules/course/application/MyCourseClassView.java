package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.CourseMemberRole;

public record MyCourseClassView(Long teachingClassId, String classCode, String className, CourseMemberRole role) {}
