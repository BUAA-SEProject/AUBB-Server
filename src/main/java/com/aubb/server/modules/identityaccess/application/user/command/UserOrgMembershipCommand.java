package com.aubb.server.modules.identityaccess.application.user.command;

import com.aubb.server.modules.identityaccess.domain.membership.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.membership.MembershipType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record UserOrgMembershipCommand(
        @NotNull Long orgUnitId,
        @NotNull MembershipType membershipType,
        @NotNull MembershipStatus membershipStatus,
        @NotNull MembershipSourceType sourceType,
        OffsetDateTime startAt,
        OffsetDateTime endAt) {}
