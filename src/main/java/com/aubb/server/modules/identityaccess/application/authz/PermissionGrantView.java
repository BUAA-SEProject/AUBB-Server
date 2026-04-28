package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;

public record PermissionGrantView(PermissionCode permission, ScopeRef scope, String source, String sourceReference) {

    public static PermissionGrantView allow(
            PermissionCode permission, ScopeRef scope, String source, String sourceReference) {
        return new PermissionGrantView(permission, scope, source, sourceReference);
    }
}
