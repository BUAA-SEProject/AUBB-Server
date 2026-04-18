package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.List;

public record AuthorizationResult(
        boolean allowed,
        String reasonCode,
        List<String> matchedRoles,
        List<AuthorizationScope> matchedScopes,
        boolean needAudit) {

    public AuthorizationResult {
        matchedRoles = matchedRoles == null ? List.of() : List.copyOf(matchedRoles);
        matchedScopes = matchedScopes == null ? List.of() : List.copyOf(matchedScopes);
    }

    public static AuthorizationResult allow(
            String reasonCode, List<String> matchedRoles, List<AuthorizationScope> matchedScopes, boolean needAudit) {
        return new AuthorizationResult(true, reasonCode, matchedRoles, matchedScopes, needAudit);
    }

    public static AuthorizationResult deny(
            String reasonCode, List<String> matchedRoles, List<AuthorizationScope> matchedScopes, boolean needAudit) {
        return new AuthorizationResult(false, reasonCode, matchedRoles, matchedScopes, needAudit);
    }
}
