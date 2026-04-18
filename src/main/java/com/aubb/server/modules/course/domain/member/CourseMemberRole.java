package com.aubb.server.modules.course.domain.member;

import com.aubb.server.modules.identityaccess.domain.membership.MembershipType;

public enum CourseMemberRole {
    INSTRUCTOR,
    CLASS_INSTRUCTOR,
    OFFERING_TA,
    TA,
    STUDENT,
    OBSERVER;

    public boolean requiresTeachingClass() {
        return this == CLASS_INSTRUCTOR || this == TA || this == STUDENT;
    }

    public MembershipType toMembershipType() {
        return switch (this) {
            case INSTRUCTOR, CLASS_INSTRUCTOR -> MembershipType.TEACHES;
            case OFFERING_TA, TA -> MembershipType.ASSISTS;
            case STUDENT -> MembershipType.ENROLLED;
            case OBSERVER -> MembershipType.MANAGES;
        };
    }
}
