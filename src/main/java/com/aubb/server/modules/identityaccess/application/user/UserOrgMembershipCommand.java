package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.modules.identityaccess.domain.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.MembershipType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record UserOrgMembershipCommand(
        @NotNull Long orgUnitId,
        @NotNull MembershipType membershipType,
        @NotNull MembershipStatus membershipStatus,
        @NotNull MembershipSourceType sourceType,
        OffsetDateTime startAt,
        OffsetDateTime endAt) {}
