package com.aubb.server.application.auth;

import com.aubb.server.application.iam.ScopeIdentityView;
import com.aubb.server.domain.iam.AccountStatus;
import java.util.List;

public record AuthenticatedUserView(
        Long userId,
        String username,
        String displayName,
        Long primaryOrgUnitId,
        AccountStatus accountStatus,
        List<ScopeIdentityView> identities) {

    public static AuthenticatedUserView from(AuthenticatedUserPrincipal principal) {
        return new AuthenticatedUserView(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getPrimaryOrgUnitId(),
                principal.getAccountStatus(),
                principal.getIdentities());
    }
}
