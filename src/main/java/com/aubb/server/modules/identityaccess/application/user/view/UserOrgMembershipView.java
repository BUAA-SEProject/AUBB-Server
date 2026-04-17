package com.aubb.server.modules.identityaccess.application.user.view;

import com.aubb.server.modules.identityaccess.domain.membership.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipType;
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
