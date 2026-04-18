package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityService;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegacyGovernanceGrantResolver implements PermissionGrantResolver {

    private final ScopeIdentityService scopeIdentityService;
    private final AuthzScopeResolutionService authzScopeResolutionService;

    @Override
    public List<PermissionGrantView> resolve(AuthenticatedUserPrincipal principal) {
        return scopeIdentityService.loadForPrincipal(principal).stream()
                .flatMap(identity -> resolveScope(identity).stream()
                        .flatMap(scope ->
                                LegacyPermissionGrantMatrix.forGovernanceRole(GovernanceRole.from(identity.roleCode()))
                                        .stream()
                                        .map(permission -> PermissionGrantView.allow(
                                                permission, scope, "LEGACY_GOVERNANCE", identity.roleCode()))))
                .toList();
    }

    private java.util.Optional<ScopeRef> resolveScope(ScopeIdentityView identity) {
        AuthorizationScopeType scopeType = mapScopeType(identity);
        if (scopeType != AuthorizationScopeType.CLASS) {
            return java.util.Optional.of(new ScopeRef(scopeType, identity.scopeOrgUnitId()));
        }
        Long teachingClassId =
                authzScopeResolutionService.findTeachingClassIdByOrgClassUnitId(identity.scopeOrgUnitId());
        return java.util.Optional.ofNullable(teachingClassId)
                .map(scopeRefId -> new ScopeRef(AuthorizationScopeType.CLASS, scopeRefId));
    }

    private AuthorizationScopeType mapScopeType(ScopeIdentityView identity) {
        OrgUnitType orgUnitType = OrgUnitType.valueOf(identity.scopeOrgType());
        return switch (orgUnitType) {
            case SCHOOL -> AuthorizationScopeType.SCHOOL;
            case COLLEGE -> AuthorizationScopeType.COLLEGE;
            case COURSE -> AuthorizationScopeType.COURSE;
            case CLASS -> AuthorizationScopeType.CLASS;
        };
    }
}
