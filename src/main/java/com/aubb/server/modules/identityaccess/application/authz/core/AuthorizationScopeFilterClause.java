package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.Set;

public record AuthorizationScopeFilterClause(
        AuthorizationScope scope, Set<String> roleCodes, RoleBindingConstraints constraints) {

    public AuthorizationScopeFilterClause {
        if (scope == null) {
            throw new IllegalArgumentException("Scope must not be null");
        }
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        constraints = constraints == null ? RoleBindingConstraints.none() : constraints;
    }
}
