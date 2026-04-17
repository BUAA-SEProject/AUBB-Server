package com.aubb.server.modules.identityaccess.application.authz.guard;

import com.aubb.server.modules.identityaccess.application.authz.AuthorizationDecision;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationRequest;
import com.aubb.server.modules.identityaccess.application.authz.PermissionGrantView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class AccountStatusGuard implements PolicyGuard {

    @Override
    public Optional<AuthorizationDecision> evaluate(AuthorizationRequest request, List<PermissionGrantView> grants) {
        if (request.principal().getAccountStatus() != AccountStatus.ACTIVE) {
            return Optional.of(AuthorizationDecision.deny("ACCOUNT_INACTIVE", grants));
        }
        return Optional.empty();
    }
}
