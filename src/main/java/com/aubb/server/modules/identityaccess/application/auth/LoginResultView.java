package com.aubb.server.modules.identityaccess.application.auth;

public record LoginResultView(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AuthenticatedUserView user,
        String refreshToken,
        long refreshExpiresInSeconds) {

    public LoginResultView(String accessToken, String tokenType, long expiresInSeconds, AuthenticatedUserView user) {
        this(accessToken, tokenType, expiresInSeconds, user, null, 0);
    }
}
