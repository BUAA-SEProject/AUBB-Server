package com.aubb.server.application.auth;

public record LoginResultView(
        String accessToken, String tokenType, long expiresInSeconds, AuthenticatedUserView user) {}
