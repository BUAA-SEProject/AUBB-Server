package com.aubb.server.modules.identityaccess.application.authz;

import java.util.List;

public record AuthorizationDecision(boolean allowed, String reasonCode, List<PermissionGrantView> grants) {

    public AuthorizationDecision {
        grants = grants == null ? List.of() : List.copyOf(grants);
    }

    public static AuthorizationDecision allow(List<PermissionGrantView> grants) {
        return new AuthorizationDecision(true, null, grants);
    }

    public static AuthorizationDecision deny(String reasonCode) {
        return new AuthorizationDecision(false, reasonCode, List.of());
    }
}
