package com.aubb.server.modules.identityaccess.domain.governance;

import com.aubb.server.modules.organization.domain.OrgUnitType;

public class GovernanceRolePolicy {

    public RoleScopeValidationResult validateScope(GovernanceRole role, OrgUnitType scopeType) {
        if (role.scopeType() != scopeType) {
            return RoleScopeValidationResult.rejected(
                    role.name() + " 只能绑定到 " + role.scopeType().name() + " 节点");
        }
        return RoleScopeValidationResult.allowed();
    }

    public boolean canGrant(GovernanceRole actorRole, GovernanceRole targetRole) {
        if (actorRole == GovernanceRole.SCHOOL_ADMIN) {
            return true;
        }
        return actorRole.rank() < targetRole.rank();
    }
}
