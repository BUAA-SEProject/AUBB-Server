package com.aubb.server.modules.identityaccess.application.authz.core;

public record RoleBindingGrant(RoleDefinition role, PermissionDefinition permission, RoleBindingDefinition binding) {

    public AuthorizationScope scope() {
        return binding.scope();
    }
}
