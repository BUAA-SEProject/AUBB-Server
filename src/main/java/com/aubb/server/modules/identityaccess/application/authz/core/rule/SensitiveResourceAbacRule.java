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
@Order(60)
public class SensitiveResourceAbacRule implements AuthorizationAbacRule {

    @Override
    public Optional<String> evaluate(
            AuthenticatedUserPrincipal principal,
            RoleBindingGrant grant,
            ResolvedAuthorizationResource resource,
            AuthorizationContext context,
            RoleBindingConstraints constraints) {
        if ((context.sensitiveAccess() || resource.sensitive())
                && !grant.permission().allowsSensitiveAccess()) {
            return Optional.of("DENY_SENSITIVE_RESOURCE");
        }
        return Optional.empty();
    }
}
