package com.aubb.server.modules.course.application.command;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import jakarta.validation.constraints.NotNull;

public record CourseMemberCommand(
        @NotNull Long userId, @NotNull CourseMemberRole memberRole, Long teachingClassId, String remark) {}
