package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;

public record AuthorizationRequest(
        AuthenticatedUserPrincipal principal,
        PermissionCode permission,
        ScopeRef scope,
        Long resourceOwnerUserId,
        boolean archivedResource) {

    public static AuthorizationRequest forPermission(
            AuthenticatedUserPrincipal principal, PermissionCode permission, ScopeRef scope) {
        return new AuthorizationRequest(principal, permission, scope, null, false);
    }

    public AuthorizationRequest withResourceOwnerUserId(Long ownerUserId) {
        return new AuthorizationRequest(principal, permission, scope, ownerUserId, archivedResource);
    }

    public AuthorizationRequest withArchivedResource(boolean resourceArchived) {
        return new AuthorizationRequest(principal, permission, scope, resourceOwnerUserId, resourceArchived);
    }
}
