package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupGrantRow;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupQueryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersistedAuthzGroupGrantResolver implements PermissionGrantResolver {

    private final AuthzGroupQueryMapper authzGroupQueryMapper;

    @Override
    public List<PermissionGrantView> resolve(AuthenticatedUserPrincipal principal) {
        return authzGroupQueryMapper.selectActiveGrantRowsByUserId(principal.getUserId()).stream()
                .map(this::toGrant)
                .distinct()
                .toList();
    }

    private PermissionGrantView toGrant(AuthzGroupGrantRow row) {
        return PermissionGrantView.allow(
                PermissionCode.fromCode(row.getPermissionCode()),
                new ScopeRef(AuthorizationScopeType.valueOf(row.getScopeType()), row.getScopeRefId()),
                "AUTHZ_GROUP",
                row.getTemplateCode() + "#" + row.getGroupId());
    }
}
