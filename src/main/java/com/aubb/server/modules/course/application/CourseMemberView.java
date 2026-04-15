package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.CourseMemberRole;
import com.aubb.server.modules.course.domain.CourseMemberSourceType;
import com.aubb.server.modules.course.domain.CourseMemberStatus;
import com.aubb.server.modules.identityaccess.application.user.UserDirectoryEntryView;
import java.time.OffsetDateTime;

public record CourseMemberView(
        Long id,
        Long offeringId,
        Long teachingClassId,
        String classCode,
        String className,
        UserDirectoryEntryView user,
        CourseMemberRole memberRole,
        CourseMemberStatus memberStatus,
        CourseMemberSourceType sourceType,
        String remark,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt) {}
