package com.aubb.server.modules.platformconfig.application.bootstrap;

public record PlatformBootstrapResult(
        Long schoolOrgUnitId,
        Long adminUserId,
        boolean schoolCreated,
        boolean adminCreated,
        boolean schoolAdminRoleCreated,
        boolean academicProfileCreated,
        boolean platformConfigCreated) {}
