package com.aubb.server.modules.identityaccess.application.authz.core;

public record RoleBindingDefinition(
        Long id,
        Long userId,
        Long roleId,
        AuthorizationScope scope,
        RoleBindingConstraints constraints,
        String status,
        Long grantedBy,
        String sourceType,
        Long sourceRefId) {}
