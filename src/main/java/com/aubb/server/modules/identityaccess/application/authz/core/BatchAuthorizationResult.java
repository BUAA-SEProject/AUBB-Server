package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.Map;

public record BatchAuthorizationResult(Map<AuthorizationResourceRef, AuthorizationResult> results) {

    public BatchAuthorizationResult {
        results = results == null ? Map.of() : Map.copyOf(results);
    }
}
