package com.aubb.server.modules.identityaccess.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AccessTokenSessionValidatorTests {

    @Mock
    private AuthSessionApplicationService authSessionApplicationService;

    @Test
    void rejectsAccessTokenWhenSessionWasRevoked() {
        AccessTokenSessionValidator validator = new AccessTokenSessionValidator(authSessionApplicationService);
        when(authSessionApplicationService.isAccessTokenActive(42L, "session-1"))
                .thenReturn(false);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("school-admin")
                .claim("tokenType", "access")
                .claim("userId", 42L)
                .claim("sid", "session-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void acceptsAccessTokenWhenSessionIsActive() {
        AccessTokenSessionValidator validator = new AccessTokenSessionValidator(authSessionApplicationService);
        when(authSessionApplicationService.isAccessTokenActive(42L, "session-1"))
                .thenReturn(true);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("school-admin")
                .claim("tokenType", "access")
                .claim("userId", 42L)
                .claim("sid", "session-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }
}
