package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.AccountStatus;
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
    private final AccountStatus accountStatus;
    private final List<ScopeIdentityView> identities;
    private final Set<String> authorityCodes;

    public AuthenticatedUserPrincipal(
            Long userId,
            String username,
            String displayName,
            Long primaryOrgUnitId,
            AccountStatus accountStatus,
            List<ScopeIdentityView> identities) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.primaryOrgUnitId = primaryOrgUnitId;
        this.accountStatus = accountStatus;
        this.identities = identities;
        this.authorityCodes = identities.stream()
                .map(ScopeIdentityView::roleCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
}
