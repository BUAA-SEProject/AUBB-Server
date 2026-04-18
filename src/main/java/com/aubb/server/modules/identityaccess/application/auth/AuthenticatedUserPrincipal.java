package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.application.user.view.AcademicProfileView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Getter
public class AuthenticatedUserPrincipal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String displayName;
    private final Long primaryOrgUnitId;
    private final String sessionId;
    private final AccountStatus accountStatus;
    private final AcademicProfileView academicProfile;
    private final List<ScopeIdentityView> identities;
    private final List<GroupBindingView> groupBindings;
    private final Set<String> permissionCodes;
    private final Long permissionVersion;
    private final Set<String> authorityCodes;

    public AuthenticatedUserPrincipal(
            Long userId,
            String username,
            String displayName,
            Long primaryOrgUnitId,
            AccountStatus accountStatus,
            AcademicProfileView academicProfile,
            List<ScopeIdentityView> identities) {
        this(
                userId,
                username,
                displayName,
                primaryOrgUnitId,
                null,
                accountStatus,
                academicProfile,
                identities,
                List.of(),
                Set.of(),
                null);
    }

    public AuthenticatedUserPrincipal(
            Long userId,
            String username,
            String displayName,
            Long primaryOrgUnitId,
            String sessionId,
            AccountStatus accountStatus,
            AcademicProfileView academicProfile,
            List<ScopeIdentityView> identities) {
        this(
                userId,
                username,
                displayName,
                primaryOrgUnitId,
                sessionId,
                accountStatus,
                academicProfile,
                identities,
                List.of(),
                Set.of(),
                null);
    }

    public AuthenticatedUserPrincipal(
            Long userId,
            String username,
            String displayName,
            Long primaryOrgUnitId,
            String sessionId,
            AccountStatus accountStatus,
            AcademicProfileView academicProfile,
            List<ScopeIdentityView> identities,
            List<GroupBindingView> groupBindings,
            Set<String> permissionCodes,
            Long permissionVersion) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.primaryOrgUnitId = primaryOrgUnitId;
        this.sessionId = sessionId;
        this.accountStatus = accountStatus;
        this.academicProfile = academicProfile;
        this.identities = List.copyOf(identities == null ? List.of() : identities);
        this.groupBindings = List.copyOf(groupBindings == null ? List.of() : groupBindings);
        this.permissionCodes = Set.copyOf(permissionCodes == null ? Set.of() : permissionCodes);
        this.permissionVersion = permissionVersion;
        LinkedHashSet<String> resolvedAuthorities = this.identities.stream()
                .map(ScopeIdentityView::roleCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        this.groupBindings.stream()
                .map(AuthenticatedUserPrincipal::authorityForBinding)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .forEach(resolvedAuthorities::add);
        this.authorityCodes = Set.copyOf(resolvedAuthorities);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return authorityCodes.stream().map(SimpleGrantedAuthority::new).toList();
    }

    public boolean hasAuthority(String authority) {
        return authorityCodes.contains(authority);
    }

    public List<String> roleCodes() {
        return authorityCodes.stream().sorted().toList();
    }

    private static java.util.Optional<String> authorityForBinding(GroupBindingView binding) {
        if (binding == null || binding.templateCode() == null) {
            return java.util.Optional.empty();
        }
        return switch (binding.templateCode()) {
            case "school-admin" -> java.util.Optional.of(GovernanceRole.SCHOOL_ADMIN.name());
            case "college-admin" -> java.util.Optional.of(GovernanceRole.COLLEGE_ADMIN.name());
            case "course-admin" -> java.util.Optional.of(GovernanceRole.COURSE_ADMIN.name());
            case "class-admin" -> java.util.Optional.of(GovernanceRole.CLASS_ADMIN.name());
            default -> java.util.Optional.empty();
        };
    }
}
