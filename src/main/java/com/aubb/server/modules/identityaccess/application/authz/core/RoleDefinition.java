package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;

public record RoleDefinition(
        Long id,
        String code,
        String name,
        String description,
        String roleCategory,
        AuthorizationScopeType scopeType,
        boolean builtin,
        String status) {}
