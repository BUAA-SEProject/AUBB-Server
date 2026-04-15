package com.aubb.server.modules.course.domain.member;

import com.aubb.server.modules.identityaccess.domain.membership.MembershipType;

public enum CourseMemberRole {
    INSTRUCTOR,
    TA,
    STUDENT,
    OBSERVER;

    public boolean requiresTeachingClass() {
        return this == TA || this == STUDENT;
    }

    public MembershipType toMembershipType() {
        return switch (this) {
            case INSTRUCTOR -> MembershipType.TEACHES;
            case TA -> MembershipType.ASSISTS;
            case STUDENT -> MembershipType.ENROLLED;
            case OBSERVER -> MembershipType.MANAGES;
        };
    }
}
