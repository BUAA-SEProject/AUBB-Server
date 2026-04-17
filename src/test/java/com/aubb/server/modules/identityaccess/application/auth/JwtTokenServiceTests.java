package com.aubb.server.modules.identityaccess.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.config.JwtSecurityProperties;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTests {

    private JwtTokenService jwtTokenService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setIssuer("AUBB-Server");
        properties.setTtl(Duration.ofHours(2));
        properties.setRefreshTtl(Duration.ofDays(14));
        properties.setSecret("test-jwt-secret-for-aubb-server-0123456789");

        jwtTokenService = new JwtTokenService(
                NimbusJwtEncoder.withSecretKey(properties.secretKey()).build(), properties);
        jwtDecoder = NimbusJwtDecoder.withSecretKey(properties.secretKey()).build();
    }

    @Test
    void issuesAccessTokenWithSessionClaimAndRefreshPayload() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                1L,
                "school-admin",
                "School Admin",
                1L,
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(new GroupBindingView("LEGACY_GOVERNANCE", "school-admin", "SCHOOL", 1L)),
                Set.of("org.unit.manage", "auth.group.manage"),
                42L);

        LoginResultView result = jwtTokenService.issueToken(principal, "session-123", "refresh-token-123", 42L);
        Jwt jwt = jwtDecoder.decode(result.accessToken());

        assertThat(result.refreshToken()).isEqualTo("refresh-token-123");
        assertThat(result.refreshExpiresInSeconds())
                .isEqualTo(Duration.ofDays(14).toSeconds());
        assertThat(jwt.getClaimAsString("sid")).isEqualTo("session-123");
        assertThat(jwt.getClaimAsString("tokenType")).isEqualTo("access");
        assertThat(((Number) jwt.getClaim("userId")).longValue()).isEqualTo(1L);
        assertThat(jwt.getClaimAsStringList("permissionCodes")).contains("org.unit.manage", "auth.group.manage");
        assertThat(((List<?>) jwt.getClaim("groupBindings"))).hasSize(1);
        assertThat(((Number) jwt.getClaim("permissionVersion")).longValue()).isEqualTo(42L);
    }
}
