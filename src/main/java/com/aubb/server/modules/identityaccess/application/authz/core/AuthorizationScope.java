package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;

public record AuthorizationScope(AuthorizationScopeType type, Long id) {

    public static final long PLATFORM_SCOPE_ID = 0L;

    public AuthorizationScope {
        if (type == null) {
            throw new IllegalArgumentException("Scope type must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("Scope id must not be null");
        }
    }

    public static AuthorizationScope platform() {
        return new AuthorizationScope(AuthorizationScopeType.PLATFORM, PLATFORM_SCOPE_ID);
    }

    public static AuthorizationScope of(AuthorizationScopeType type, Long id) {
        if (type == AuthorizationScopeType.PLATFORM) {
            return platform();
        }
        return new AuthorizationScope(type, id);
    }
}
