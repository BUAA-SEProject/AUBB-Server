package com.aubb.server.modules.identityaccess.application.authz.core;

import java.time.OffsetDateTime;

public record ResolvedAuthorizationResource(
        AuthorizationResourceRef resourceRef,
        AuthorizationScopePath scopePath,
        Long ownerUserId,
        boolean published,
        boolean archived,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        boolean sensitive) {

    public ResolvedAuthorizationResource {
        if (resourceRef == null) {
            throw new IllegalArgumentException("Resource ref must not be null");
        }
        if (scopePath == null) {
            throw new IllegalArgumentException("Scope path must not be null");
        }
    }
}
