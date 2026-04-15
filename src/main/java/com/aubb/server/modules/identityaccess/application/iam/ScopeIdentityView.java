package com.aubb.server.modules.identityaccess.application.iam;

public record ScopeIdentityView(String roleCode, Long scopeOrgUnitId, String scopeOrgType, String scopeOrgName) {}
