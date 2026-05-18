package com.aubb.server.config;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedPrincipalLoader;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.application.user.view.AcademicProfileView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtPrincipalAuthenticationConverter implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final AuthenticatedPrincipalLoader authenticatedPrincipalLoader;

    @Override
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        Long userId = readLong(jwt.getClaim("userId"));
        AuthenticatedUserPrincipal reloadedPrincipal =
                userId == null ? null : authenticatedPrincipalLoader.loadPrincipal(userId);
        AuthenticatedUserPrincipal principal = reloadedPrincipal == null
                ? readPrincipalFromJwt(jwt, userId)
                : withTokenMetadata(reloadedPrincipal, jwt);
        Collection<GrantedAuthority> authorities = reloadedPrincipal == null
                ? readAuthorities(jwt)
                : principal.authorities().stream()
                        .map(authority -> (GrantedAuthority) authority)
                        .toList();
        return new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), authorities);
    }

    private AuthenticatedUserPrincipal readPrincipalFromJwt(Jwt jwt, Long userId) {
        List<ScopeIdentityView> identities = readIdentities(jwt);
        List<GroupBindingView> groupBindings = readGroupBindings(jwt);
        Set<String> permissionCodes = readPermissionCodes(jwt);
        return new AuthenticatedUserPrincipal(
                userId,
                jwt.getSubject(),
                jwt.getClaimAsString("displayName"),
                readLong(jwt.getClaim("primaryOrgUnitId")),
                jwt.getClaimAsString("sid"),
                AccountStatus.valueOf(jwt.getClaimAsString("accountStatus")),
                readAcademicProfile(jwt),
                identities,
                groupBindings,
                permissionCodes,
                readLong(jwt.getClaim("permissionVersion")),
                readBoolean(jwt.getClaim("roleBindingSnapshot")));
    }

    private AuthenticatedUserPrincipal withTokenMetadata(AuthenticatedUserPrincipal principal, Jwt jwt) {
        Long permissionVersion = readLong(jwt.getClaim("permissionVersion"));
        return new AuthenticatedUserPrincipal(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getPrimaryOrgUnitId(),
                jwt.getClaimAsString("sid"),
                principal.getAccountStatus(),
                principal.getAcademicProfile(),
                principal.getIdentities(),
                principal.getGroupBindings(),
                principal.getPermissionCodes(),
                permissionVersion == null ? principal.getPermissionVersion() : permissionVersion,
                principal.isRoleBindingSnapshot());
    }

    private Collection<GrantedAuthority> readAuthorities(Jwt jwt) {
        List<String> authorityCodes = jwt.getClaimAsStringList("authorities");
        if (authorityCodes == null) {
            return List.of();
        }
        return authorityCodes.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toList();
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

    @SuppressWarnings("unchecked")
    private List<GroupBindingView> readGroupBindings(Jwt jwt) {
        Object groupBindingsClaim = jwt.getClaim("groupBindings");
        if (!(groupBindingsClaim instanceof List<?> bindings)) {
            return List.of();
        }
        return bindings.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(binding -> new GroupBindingView(
                        String.valueOf(binding.get("source")),
                        String.valueOf(binding.get("templateCode")),
                        String.valueOf(binding.get("scopeType")),
                        readLong(binding.get("scopeRefId"))))
                .toList();
    }

    private Set<String> readPermissionCodes(Jwt jwt) {
        List<String> codes = jwt.getClaimAsStringList("permissionCodes");
        if (codes == null) {
            return Set.of();
        }
        return codes.stream().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    private AcademicProfileView readAcademicProfile(Jwt jwt) {
        Object academicProfileClaim = jwt.getClaim("academicProfile");
        if (!(academicProfileClaim instanceof Map<?, ?> rawProfile)) {
            return null;
        }
        return new AcademicProfileView(
                readLong(rawProfile.get("id")),
                readLong(rawProfile.get("userId")),
                readString(rawProfile.get("academicId")),
                readString(rawProfile.get("realName")),
                readEnum(rawProfile.get("identityType"), AcademicIdentityType.class),
                readEnum(rawProfile.get("profileStatus"), AcademicProfileStatus.class),
                readString(rawProfile.get("phone")));
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

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean readBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private <E extends Enum<E>> E readEnum(Object value, Class<E> enumType) {
        if (value == null) {
            return null;
        }
        return Enum.valueOf(enumType, String.valueOf(value));
    }
}
