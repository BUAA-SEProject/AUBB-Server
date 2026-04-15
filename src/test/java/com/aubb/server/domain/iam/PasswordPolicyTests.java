package com.aubb.server.domain.iam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTests {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void acceptsPasswordWithMinimumLengthLettersAndDigits() {
        PasswordValidationResult result = passwordPolicy.validate("Password123");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        PasswordValidationResult result = passwordPolicy.validate("PasswordOnly");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("密码必须同时包含字母和数字");
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        PasswordValidationResult result = passwordPolicy.validate("P1short");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("密码长度不能少于 8 位");
    }
}
