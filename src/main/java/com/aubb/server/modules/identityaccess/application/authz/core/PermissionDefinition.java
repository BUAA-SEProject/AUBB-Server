package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.Set;

public record PermissionDefinition(
        Long id, String code, String resourceType, String action, String description, boolean sensitive) {

    private static final Set<String> WRITE_ACTIONS = Set.of(
            "manage",
            "create",
            "edit",
            "delete",
            "publish",
            "close",
            "archive",
            "grade",
            "regrade",
            "comment",
            "override",
            "import",
            "upload",
            "save",
            "submit",
            "review",
            "config");

    private static final Set<String> SENSITIVE_ACCESS_CODES =
            Set.of("submission.read_source", "judge.view_hidden", "judge.config");

    public boolean isWriteOperation() {
        return WRITE_ACTIONS.contains(action);
    }

    public boolean allowsSensitiveAccess() {
        return SENSITIVE_ACCESS_CODES.contains(code);
    }
}
