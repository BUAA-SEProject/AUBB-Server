package com.aubb.server.modules.identityaccess.domain.authz;

import java.util.Arrays;

public enum AuthorizationScopeType {
    PLATFORM,
    SCHOOL,
    COLLEGE,
    COURSE,
    OFFERING,
    CLASS;

    public String databaseValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static AuthorizationScopeType fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(scopeType -> scopeType.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported scope type: " + value));
    }
}
