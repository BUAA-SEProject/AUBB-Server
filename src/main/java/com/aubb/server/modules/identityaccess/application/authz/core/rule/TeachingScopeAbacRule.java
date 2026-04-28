package com.aubb.server.modules.identityaccess.application.authz.core.rule;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationContext;
import com.aubb.server.modules.identityaccess.application.authz.core.ResolvedAuthorizationResource;
import com.aubb.server.modules.identityaccess.application.authz.core.RoleBindingConstraints;
import com.aubb.server.modules.identityaccess.application.authz.core.RoleBindingGrant;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class TeachingScopeAbacRule implements AuthorizationAbacRule {

    @Override
    public Optional<String> evaluate(
            AuthenticatedUserPrincipal principal,
            RoleBindingGrant grant,
            ResolvedAuthorizationResource resource,
            AuthorizationContext context,
            RoleBindingConstraints constraints) {
        if (constraints.teachingScopeOnly() && !resource.scopePath().hasTeachingScope()) {
            return Optional.of("DENY_OUTSIDE_TEACHING_SCOPE");
        }
        return Optional.empty();
    }
}
