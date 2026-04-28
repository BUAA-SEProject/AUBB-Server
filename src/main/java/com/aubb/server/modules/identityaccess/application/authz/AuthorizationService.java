package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.application.authz.guard.PolicyGuardRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @deprecated 核心业务鉴权已迁移到 {@code PermissionAuthorizationService + role_bindings}。
 *     本类仅保留给 explain、兼容回退和旧权限矩阵收口阶段使用。
 */
@Deprecated(forRemoval = false)
@Service
public class AuthorizationService {

    private final List<PermissionGrantResolver> grantResolvers;
    private final PolicyGuardRegistry policyGuardRegistry;

    @Autowired
    public AuthorizationService(List<PermissionGrantResolver> grantResolvers, PolicyGuardRegistry policyGuardRegistry) {
        this.grantResolvers = List.copyOf(grantResolvers);
        this.policyGuardRegistry = policyGuardRegistry;
    }

    AuthorizationService(List<PermissionGrantResolver> grantResolvers) {
        this(grantResolvers, PolicyGuardRegistry.noop());
    }

    public AuthorizationDecision decide(AuthorizationRequest request) {
        List<PermissionGrantView> grants = grantResolvers.stream()
                .flatMap(resolver -> resolver.resolve(request.principal()).stream())
                .filter(grant -> grant.permission() == request.permission())
                .filter(grant -> request.scope().isCoveredBy(grant.scope()))
                .toList();
        if (grants.isEmpty()) {
            return AuthorizationDecision.deny("NO_PERMISSION");
        }
        return policyGuardRegistry.evaluate(request, grants).orElseGet(() -> AuthorizationDecision.allow(grants));
    }
}
