package com.aubb.server.domain.iam;

import com.aubb.server.domain.organization.OrgUnitType;
import java.util.Arrays;

public enum GovernanceRole {
    SCHOOL_ADMIN(OrgUnitType.SCHOOL, 1),
    COLLEGE_ADMIN(OrgUnitType.COLLEGE, 2),
    COURSE_ADMIN(OrgUnitType.COURSE, 3),
    CLASS_ADMIN(OrgUnitType.CLASS, 4);

    private final OrgUnitType scopeType;
    private final int rank;

    GovernanceRole(OrgUnitType scopeType, int rank) {
        this.scopeType = scopeType;
        this.rank = rank;
    }

    public OrgUnitType scopeType() {
        return scopeType;
    }

    public int rank() {
        return rank;
    }

    public boolean canManageDescendantCreation(OrgUnitType childType) {
        return switch (this) {
            case SCHOOL_ADMIN ->
                childType == OrgUnitType.COLLEGE || childType == OrgUnitType.COURSE || childType == OrgUnitType.CLASS;
            case COLLEGE_ADMIN -> childType == OrgUnitType.COURSE || childType == OrgUnitType.CLASS;
            case COURSE_ADMIN -> childType == OrgUnitType.CLASS;
            case CLASS_ADMIN -> false;
        };
    }

    public static GovernanceRole from(String code) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported governance role: " + code));
    }
}
