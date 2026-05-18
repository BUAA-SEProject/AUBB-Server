package com.aubb.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedPrincipalLoader;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class JwtPrincipalAuthenticationConverterTests {

    @Mock
    private AuthenticatedPrincipalLoader authenticatedPrincipalLoader;

    @Test
    void convertShouldReloadPermissionSnapshotWhenTokenIsCompact() {
        JwtPrincipalAuthenticationConverter converter =
                new JwtPrincipalAuthenticationConverter(authenticatedPrincipalLoader);
        when(authenticatedPrincipalLoader.loadPrincipal(7L)).thenReturn(reloadedPrincipal());

        UsernamePasswordAuthenticationToken authentication = converter.convert(compactJwt());

        AuthenticatedUserPrincipal principal = (AuthenticatedUserPrincipal) authentication.getPrincipal();
        assertThat(principal.getSessionId()).isEqualTo("session-7");
        assertThat(principal.getPermissionCodes()).contains("class.read", "member.manage");
        assertThat(principal.getGroupBindings())
                .containsExactly(new GroupBindingView("LEGACY_GOVERNANCE", "class-admin", "CLASS", 100L));
        assertThat(principal.hasAuthority("CLASS_ADMIN")).isTrue();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("CLASS_ADMIN");
        verify(authenticatedPrincipalLoader).loadPrincipal(7L);
    }

    private Jwt compactJwt() {
        Instant issuedAt = Instant.parse("2026-05-18T04:00:00Z");
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject("teacher")
                .issuer("AUBB-Server")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("tokenType", "access")
                .claim("sid", "session-7")
                .claim("userId", 7L)
                .claim("displayName", "Teacher")
                .claim("accountStatus", "ACTIVE")
                .claim("authorities", List.of("CLASS_ADMIN"))
                .claim("identities", List.of())
                .claim("roleBindingSnapshot", true)
                .claim("permissionVersion", 123L)
                .build();
    }

    private AuthenticatedUserPrincipal reloadedPrincipal() {
        return new AuthenticatedUserPrincipal(
                7L,
                "teacher",
                "Teacher",
                100L,
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(new ScopeIdentityView("CLASS_ADMIN", 100L, "CLASS", "SE Class 1")),
                List.of(new GroupBindingView("LEGACY_GOVERNANCE", "class-admin", "CLASS", 100L)),
                Set.of("class.read", "member.manage"),
                null,
                true);
    }
}
