package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.AccountStatus;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import java.time.OffsetDateTime;
import java.util.List;

public record UserView(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        AcademicProfileView academicProfile,
        Long primaryOrgUnitId,
        OrgUnitSummaryView primaryOrgUnit,
        AccountStatus accountStatus,
        List<ScopeIdentityView> identities,
        List<UserOrgMembershipView> memberships,
        OffsetDateTime lastLoginAt,
        OffsetDateTime lockedUntil,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
