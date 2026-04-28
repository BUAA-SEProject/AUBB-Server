package com.aubb.server.modules.identityaccess.application.authz.core.rule;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationContext;
import com.aubb.server.modules.identityaccess.application.authz.core.ResolvedAuthorizationResource;
import com.aubb.server.modules.identityaccess.application.authz.core.RoleBindingConstraints;
import com.aubb.server.modules.identityaccess.application.authz.core.RoleBindingGrant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class OwnerAbacRule implements AuthorizationAbacRule {

    @Override
    public Optional<String> evaluate(
            AuthenticatedUserPrincipal principal,
            RoleBindingGrant grant,
            ResolvedAuthorizationResource resource,
            AuthorizationContext context,
            RoleBindingConstraints constraints) {
        if (!constraints.ownerOnly()) {
            return Optional.empty();
        }
        if (resource.ownerUserId() == null || !Objects.equals(resource.ownerUserId(), principal.getUserId())) {
            return Optional.of("DENY_NOT_OWNER");
        }
        return Optional.empty();
    }
}
