package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.List;

public record AuthorizationScopeFilter(String permissionCode, List<AuthorizationScopeFilterClause> clauses) {

    public AuthorizationScopeFilter {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("Permission code must not be blank");
        }
        clauses = clauses == null ? List.of() : List.copyOf(clauses);
    }
}
