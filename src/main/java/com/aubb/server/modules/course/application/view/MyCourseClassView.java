package com.aubb.server.modules.course.application.view;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;

public record MyCourseClassView(Long teachingClassId, String classCode, String className, CourseMemberRole role) {}
