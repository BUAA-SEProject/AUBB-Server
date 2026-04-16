package com.aubb.server.modules.identityaccess.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenSessionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCESS_TOKEN = new OAuth2Error("invalid_token", "访问令牌已失效或会话已被撤销", null);

    private final AuthSessionApplicationService authSessionApplicationService;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String tokenType = token.getClaimAsString("tokenType");
        if (!"access".equals(tokenType)) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCESS_TOKEN);
        }

        Long userId = readLong(token.getClaim("userId"));
        String sessionId = token.getClaimAsString("sid");
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCESS_TOKEN);
        }

        if (!authSessionApplicationService.isAccessTokenActive(userId, sessionId)) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCESS_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
