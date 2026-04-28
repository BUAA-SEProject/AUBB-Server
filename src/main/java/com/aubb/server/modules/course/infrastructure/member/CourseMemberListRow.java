package com.aubb.server.modules.course.infrastructure.member;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CourseMemberListRow {

    private Long id;
    private Long offeringId;
    private Long teachingClassId;
    private String classCode;
    private String className;
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    private String userPhone;
    private Long academicProfileId;
    private String academicId;
    private String academicRealName;
    private String academicIdentityType;
    private String academicProfileStatus;
    private String academicPhone;
    private String memberRole;
    private String memberStatus;
    private String sourceType;
    private String remark;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;
}
