package com.aubb.server.domain.organization;

public record OrganizationValidationResult(boolean valid, int childLevel, String reason) {

    public static OrganizationValidationResult allowed(int childLevel) {
        return new OrganizationValidationResult(true, childLevel, null);
    }

    public static OrganizationValidationResult rejected(String reason) {
        return new OrganizationValidationResult(false, 0, reason);
    }
}
