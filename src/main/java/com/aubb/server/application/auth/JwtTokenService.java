package com.aubb.server.application.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration ttl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${aubb.security.jwt.issuer:aubb-server}") String issuer,
            @Value("${aubb.security.jwt.ttl:PT2H}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public LoginResultView issueToken(AuthenticatedUserPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(principal.getUsername())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("userId", principal.getUserId())
                .claim("displayName", principal.getDisplayName())
                .claim("primaryOrgUnitId", principal.getPrimaryOrgUnitId())
                .claim("accountStatus", principal.getAccountStatus().name())
                .claim("authorities", principal.roleCodes())
                .claim(
                        "identities",
                        principal.getIdentities().stream()
                                .map(identity -> Map.<String, Object>of(
                                        "roleCode", identity.roleCode(),
                                        "scopeOrgUnitId", identity.scopeOrgUnitId(),
                                        "scopeOrgType", identity.scopeOrgType(),
                                        "scopeOrgName", identity.scopeOrgName()))
                                .toList())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new LoginResultView(token, "Bearer", ttl.toSeconds(), AuthenticatedUserView.from(principal));
    }

    public Duration ttl() {
        return ttl;
    }
}
