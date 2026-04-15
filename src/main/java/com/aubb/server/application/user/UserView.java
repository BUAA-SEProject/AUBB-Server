package com.aubb.server.application.user;

import com.aubb.server.application.iam.ScopeIdentityView;
import com.aubb.server.application.organization.OrgUnitSummaryView;
import com.aubb.server.domain.iam.AccountStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record UserView(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        Long primaryOrgUnitId,
        OrgUnitSummaryView primaryOrgUnit,
        AccountStatus accountStatus,
        List<ScopeIdentityView> identities,
        OffsetDateTime lastLoginAt,
        OffsetDateTime lockedUntil,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
