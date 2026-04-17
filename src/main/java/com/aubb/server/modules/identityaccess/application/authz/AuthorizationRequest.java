package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;

public record AuthorizationRequest(
        AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope, Long resourceOwnerUserId) {

    public static AuthorizationRequest forPermission(
            AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope) {
        return new AuthorizationRequest(principal, permission, scope, null);
    }

    public AuthorizationRequest withResourceOwnerUserId(Long ownerUserId) {
        return new AuthorizationRequest(principal, permission, scope, ownerUserId);
    }
}
