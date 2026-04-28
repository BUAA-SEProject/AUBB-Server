package com.aubb.server.modules.identityaccess.application.authz.core;

public record AuthorizationResourceRef(AuthorizationResourceType type, Long id) {

    public AuthorizationResourceRef {
        if (type == null) {
            throw new IllegalArgumentException("Resource type must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("Resource id must not be null");
        }
    }
}
