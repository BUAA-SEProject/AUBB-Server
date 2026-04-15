package com.aubb.server.config;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.AccountStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtPrincipalAuthenticationConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        List<String> authorityCodes = jwt.getClaimAsStringList("authorities");
        Collection<GrantedAuthority> authorities;
        if (authorityCodes == null) {
            authorities = List.of();
        } else {
            authorities = authorityCodes.stream()
                    .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                    .toList();
        }
        List<ScopeIdentityView> identities = readIdentities(jwt);
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                readLong(jwt.getClaim("userId")),
                jwt.getSubject(),
                jwt.getClaimAsString("displayName"),
                readLong(jwt.getClaim("primaryOrgUnitId")),
                AccountStatus.valueOf(jwt.getClaimAsString("accountStatus")),
                identities);
        return new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), authorities);
    }

    @SuppressWarnings("unchecked")
    private List<ScopeIdentityView> readIdentities(Jwt jwt) {
        Object identitiesClaim = jwt.getClaim("identities");
        if (!(identitiesClaim instanceof List<?> identities)) {
            return List.of();
        }
        return identities.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(identity -> new ScopeIdentityView(
                        String.valueOf(identity.get("roleCode")),
                        readLong(identity.get("scopeOrgUnitId")),
                        String.valueOf(identity.get("scopeOrgType")),
                        String.valueOf(identity.get("scopeOrgName"))))
                .toList();
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
