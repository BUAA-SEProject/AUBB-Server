package com.aubb.server.modules.identityaccess.application.authz.core;

import java.time.OffsetDateTime;

public record AuthorizationContext(OffsetDateTime requestTime, boolean sensitiveAccess) {

    public AuthorizationContext {
        requestTime = requestTime == null ? OffsetDateTime.now() : requestTime;
    }

    public static AuthorizationContext of(OffsetDateTime requestTime) {
        return new AuthorizationContext(requestTime, false);
    }

    public AuthorizationContext withSensitiveAccess(boolean sensitiveAccess) {
        return new AuthorizationContext(requestTime, sensitiveAccess);
    }
}
