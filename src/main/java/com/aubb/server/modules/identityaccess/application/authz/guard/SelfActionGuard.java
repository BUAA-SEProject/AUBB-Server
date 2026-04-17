package com.aubb.server.modules.identityaccess.application.authz.guard;

import com.aubb.server.modules.identityaccess.application.authz.AuthorizationDecision;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationRequest;
import com.aubb.server.modules.identityaccess.application.authz.PermissionGrantView;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class SelfActionGuard implements PolicyGuard {

    private static final Set<PermissionCode> SELF_FORBIDDEN_PERMISSIONS = Set.of(
            PermissionCode.SUBMISSION_GRADE,
            PermissionCode.GRADE_OVERRIDE,
            PermissionCode.APPEAL_REVIEW,
            PermissionCode.LAB_REPORT_REVIEW);

    @Override
    public Optional<AuthorizationDecision> evaluate(AuthorizationRequest request, List<PermissionGrantView> grants) {
        if (SELF_FORBIDDEN_PERMISSIONS.contains(request.permission())
                && request.resourceOwnerUserId() != null
                && Objects.equals(request.principal().getUserId(), request.resourceOwnerUserId())) {
            return Optional.of(AuthorizationDecision.deny("SELF_ACTION_FORBIDDEN", grants));
        }
        return Optional.empty();
    }
}
