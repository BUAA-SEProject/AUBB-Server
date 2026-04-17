package com.aubb.server.modules.identityaccess.application.authz.guard;

import com.aubb.server.modules.identityaccess.application.authz.AuthorizationDecision;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationRequest;
import com.aubb.server.modules.identityaccess.application.authz.PermissionGrantView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PolicyGuardRegistry {

    private final List<PolicyGuard> guards;

    public PolicyGuardRegistry(List<PolicyGuard> guards) {
        this.guards = List.copyOf(guards);
    }

    public Optional<AuthorizationDecision> evaluate(AuthorizationRequest request, List<PermissionGrantView> grants) {
        return guards.stream()
                .map(guard -> guard.evaluate(request, grants))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static PolicyGuardRegistry noop() {
        return new PolicyGuardRegistry(List.of());
    }
}
