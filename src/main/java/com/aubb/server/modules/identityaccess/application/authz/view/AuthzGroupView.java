package com.aubb.server.modules.identityaccess.application.authz.view;

public record AuthzGroupView(
        Long id,
        String templateCode,
        String scopeType,
        Long scopeRefId,
        String displayName,
        boolean managedBySystem,
        String status,
        long memberCount) {}
