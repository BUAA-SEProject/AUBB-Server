package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.application.user.AcademicProfileView;
import com.aubb.server.modules.identityaccess.domain.AccountStatus;
import java.util.List;

public record AuthenticatedUserView(
        Long userId,
        String username,
        String displayName,
        Long primaryOrgUnitId,
        AccountStatus accountStatus,
        AcademicProfileView academicProfile,
        List<ScopeIdentityView> identities) {

    public static AuthenticatedUserView from(AuthenticatedUserPrincipal principal) {
        return new AuthenticatedUserView(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getPrimaryOrgUnitId(),
                principal.getAccountStatus(),
                principal.getAcademicProfile(),
                principal.getIdentities());
    }
}
