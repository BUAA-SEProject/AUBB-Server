package com.aubb.server.modules.identityaccess.application.authz.guard;

import com.aubb.server.modules.identityaccess.application.authz.AuthorizationDecision;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationRequest;
import com.aubb.server.modules.identityaccess.application.authz.PermissionGrantView;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ArchivedWriteGuard implements PolicyGuard {

    @Override
    public Optional<AuthorizationDecision> evaluate(AuthorizationRequest request, List<PermissionGrantView> grants) {
        if (request.archivedResource() && request.permission().isWriteAction()) {
            return Optional.of(AuthorizationDecision.deny("ARCHIVED_RESOURCE_READ_ONLY", grants));
        }
        return Optional.empty();
    }
}
