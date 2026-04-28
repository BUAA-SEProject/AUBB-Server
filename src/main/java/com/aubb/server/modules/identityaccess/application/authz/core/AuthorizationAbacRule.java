package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import java.util.Optional;

public interface AuthorizationAbacRule {

    Optional<String> evaluate(
            AuthenticatedUserPrincipal principal,
            RoleBindingGrant grant,
            ResolvedAuthorizationResource resource,
            AuthorizationContext context,
            RoleBindingConstraints constraints);
}
