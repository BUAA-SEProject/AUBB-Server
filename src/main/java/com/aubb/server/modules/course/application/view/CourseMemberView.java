package com.aubb.server.modules.course.application.view;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberSourceType;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.identityaccess.application.user.view.UserDirectoryEntryView;
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
