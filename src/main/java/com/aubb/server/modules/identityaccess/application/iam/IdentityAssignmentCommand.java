package com.aubb.server.modules.identityaccess.application.iam;

import com.aubb.server.modules.identityaccess.domain.GovernanceRole;

public record IdentityAssignmentCommand(Long scopeOrgUnitId, GovernanceRole roleCode) {}
