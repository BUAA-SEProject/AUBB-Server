package com.aubb.server.modules.identityaccess.domain;

public record PasswordValidationResult(boolean valid, String reason) {

    public static PasswordValidationResult passed() {
        return new PasswordValidationResult(true, null);
    }

    public static PasswordValidationResult failed(String reason) {
        return new PasswordValidationResult(false, reason);
    }
}
