package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.config.JwtSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtEncoder jwtEncoder;
    private final JwtSecurityProperties jwtSecurityProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtSecurityProperties jwtSecurityProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtSecurityProperties = jwtSecurityProperties;
    }

    public LoginResultView issueToken(AuthenticatedUserPrincipal principal, String sessionId, String refreshToken) {
        return issueToken(principal, sessionId, refreshToken, null);
    }

    public LoginResultView issueToken(
            AuthenticatedUserPrincipal principal, String sessionId, String refreshToken, Long permissionVersion) {
        Instant issuedAt = Instant.now();
        Duration ttl = jwtSecurityProperties.getTtl();
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtSecurityProperties.getIssuer())
                .subject(principal.getUsername())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("tokenType", ACCESS_TOKEN_TYPE)
                .claim("sid", sessionId)
                .claim("userId", principal.getUserId())
                .claim("displayName", principal.getDisplayName())
                .claim("primaryOrgUnitId", principal.getPrimaryOrgUnitId())
                .claim("accountStatus", principal.getAccountStatus().name())
                .claim("authorities", principal.roleCodes())
                .claim(
                        "permissionCodes",
                        principal.getPermissionCodes().stream().sorted().toList())
                .claim(
                        "groupBindings",
                        principal.getGroupBindings().stream()
                                .map(binding -> Map.<String, Object>of(
                                        "source", binding.source(),
                                        "templateCode", binding.templateCode(),
                                        "scopeType", binding.scopeType(),
                                        "scopeRefId", binding.scopeRefId()))
                                .toList())
                .claim(
                        "identities",
                        principal.getIdentities().stream()
                                .map(identity -> Map.<String, Object>of(
                                        "roleCode", identity.roleCode(),
                                        "scopeOrgUnitId", identity.scopeOrgUnitId(),
                                        "scopeOrgType", identity.scopeOrgType(),
                                        "scopeOrgName", identity.scopeOrgName()))
                                .toList());
        if (permissionVersion != null) {
            claimsBuilder.claim("permissionVersion", permissionVersion);
        }
        Map<String, Object> academicProfileClaim = academicProfileClaim(principal);
        if (academicProfileClaim != null) {
            claimsBuilder.claim("academicProfile", academicProfileClaim);
        }
        JwtClaimsSet claims = claimsBuilder.build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new LoginResultView(
                token,
                "Bearer",
                ttl.toSeconds(),
                AuthenticatedUserView.from(principal),
                refreshToken,
                refreshTtl().toSeconds());
    }

    public Duration ttl() {
        return jwtSecurityProperties.getTtl();
    }

    public Duration refreshTtl() {
        return jwtSecurityProperties.getRefreshTtl();
    }

    private Map<String, Object> academicProfileClaim(AuthenticatedUserPrincipal principal) {
        if (principal.getAcademicProfile() == null) {
            return null;
        }
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("id", principal.getAcademicProfile().id());
        claim.put("userId", principal.getAcademicProfile().userId());
        claim.put("academicId", principal.getAcademicProfile().academicId());
        claim.put("realName", principal.getAcademicProfile().realName());
        claim.put("identityType", principal.getAcademicProfile().identityType().name());
        claim.put(
                "profileStatus", principal.getAcademicProfile().profileStatus().name());
        claim.put("phone", principal.getAcademicProfile().phone());
        return claim;
    }
}
