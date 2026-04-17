package com.aubb.server.modules.identityaccess.domain.account;

public class PasswordPolicy {

    public PasswordValidationResult validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            return PasswordValidationResult.failed("密码长度不能少于 8 位");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return PasswordValidationResult.failed("密码必须同时包含字母和数字");
        }
        return PasswordValidationResult.passed();
    }
}
