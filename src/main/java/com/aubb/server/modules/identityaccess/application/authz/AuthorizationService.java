package com.aubb.server.modules.identityaccess.application.authz;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final List<PermissionGrantResolver> grantResolvers;

    public AuthorizationDecision decide(AuthorizationRequest request) {
        List<PermissionGrantView> grants = grantResolvers.stream()
                .flatMap(resolver -> resolver.resolve(request.principal()).stream())
                .filter(grant -> grant.permission() == request.permission())
                .filter(grant -> request.scope().isCoveredBy(grant.scope()))
                .toList();
        if (grants.isEmpty()) {
            return AuthorizationDecision.deny("NO_PERMISSION");
        }
        return AuthorizationDecision.allow(grants);
    }
}
