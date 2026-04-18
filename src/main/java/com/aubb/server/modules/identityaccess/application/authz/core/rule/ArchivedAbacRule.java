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
@Order(40)
public class ArchivedAbacRule implements AuthorizationAbacRule {

    @Override
    public Optional<String> evaluate(
            AuthenticatedUserPrincipal principal,
            RoleBindingGrant grant,
            ResolvedAuthorizationResource resource,
            AuthorizationContext context,
            RoleBindingConstraints constraints) {
        if (constraints.archivedReadOnly()
                && resource.archived()
                && grant.permission().isWriteOperation()) {
            return Optional.of("DENY_ARCHIVED_READ_ONLY");
        }
        return Optional.empty();
    }
}
