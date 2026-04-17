package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import java.util.List;

@FunctionalInterface
public interface PermissionGrantResolver {

    List<PermissionGrantView> resolve(AuthenticatedUserPrincipal principal);
}
