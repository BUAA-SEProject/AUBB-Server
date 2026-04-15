package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.modules.identityaccess.domain.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.MembershipType;
import com.aubb.server.modules.organization.application.OrgUnitSummaryView;
import java.time.OffsetDateTime;

public record UserOrgMembershipView(
        Long id,
        Long orgUnitId,
        OrgUnitSummaryView orgUnit,
        MembershipType membershipType,
        MembershipStatus membershipStatus,
        MembershipSourceType sourceType,
        OffsetDateTime startAt,
        OffsetDateTime endAt) {}
