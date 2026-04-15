package com.aubb.server.domain.iam;

public record RoleScopeValidationResult(boolean valid, String reason) {

    public static RoleScopeValidationResult allowed() {
        return new RoleScopeValidationResult(true, null);
    }

    public static RoleScopeValidationResult rejected(String reason) {
        return new RoleScopeValidationResult(false, reason);
    }
}
