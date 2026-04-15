package com.aubb.server.modules.identityaccess.application.auth;

public record LoginResultView(
        String accessToken, String tokenType, long expiresInSeconds, AuthenticatedUserView user) {}
