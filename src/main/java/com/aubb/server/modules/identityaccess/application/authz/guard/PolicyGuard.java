package com.aubb.server.modules.identityaccess.application.authz.guard;

import com.aubb.server.modules.identityaccess.application.authz.AuthorizationDecision;
import com.aubb.server.modules.identityaccess.application.authz.AuthorizationRequest;
import com.aubb.server.modules.identityaccess.application.authz.PermissionGrantView;
import java.util.List;
import java.util.Optional;

public interface PolicyGuard {

    Optional<AuthorizationDecision> evaluate(AuthorizationRequest request, List<PermissionGrantView> grants);
}
