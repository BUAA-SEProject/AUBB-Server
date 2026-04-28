package com.aubb.server.modules.identityaccess.application.authz.view;

import java.time.OffsetDateTime;

public record AuthzGroupMemberView(
        Long groupId, Long userId, String sourceType, OffsetDateTime joinedAt, OffsetDateTime expiresAt) {}
