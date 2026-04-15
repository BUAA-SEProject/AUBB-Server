package com.aubb.server.application.iam;

import com.aubb.server.domain.iam.GovernanceRole;

public record IdentityAssignmentCommand(Long scopeOrgUnitId, GovernanceRole roleCode) {}
