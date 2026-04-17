package com.aubb.server.modules.identityaccess.application.authz.view;

import java.util.List;

public record AuthzExplainView(
        Long userId,
        String permissionCode,
        String scopeType,
        Long scopeRefId,
        boolean allowed,
        String reasonCode,
        List<AuthzGrantView> grants) {

    public record AuthzGrantView(String source, String sourceReference, String scopeType, Long scopeRefId) {}
}
