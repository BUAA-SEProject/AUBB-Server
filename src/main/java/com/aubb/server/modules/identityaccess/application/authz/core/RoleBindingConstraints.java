package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.Map;

public record RoleBindingConstraints(
        boolean ownerOnly,
        boolean publishedOnly,
        boolean archivedReadOnly,
        boolean timeWindowOnly,
        boolean teachingScopeOnly) {

    public static RoleBindingConstraints none() {
        return new RoleBindingConstraints(false, false, false, false, false);
    }

    public static RoleBindingConstraints fromMap(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return none();
        }
        return new RoleBindingConstraints(
                readBoolean(payload, "ownerOnly", "owner_only"),
                readBoolean(payload, "publishedOnly", "published_only"),
                readBoolean(payload, "archivedReadOnly", "archived_read_only"),
                readBoolean(payload, "timeWindowOnly", "time_window_only"),
                readBoolean(payload, "teachingScopeOnly", "teaching_scope_only"));
    }

    public RoleBindingConstraints merge(RoleBindingConstraints other) {
        if (other == null) {
            return this;
        }
        return new RoleBindingConstraints(
                ownerOnly || other.ownerOnly,
                publishedOnly || other.publishedOnly,
                archivedReadOnly || other.archivedReadOnly,
                timeWindowOnly || other.timeWindowOnly,
                teachingScopeOnly || other.teachingScopeOnly);
    }

    private static boolean readBoolean(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
        }
        return false;
    }
}
