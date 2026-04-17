package com.aubb.server.modules.identityaccess.application.iam;

import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;

public record IdentityAssignmentCommand(Long scopeOrgUnitId, GovernanceRole roleCode) {}
