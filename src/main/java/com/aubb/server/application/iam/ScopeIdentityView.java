package com.aubb.server.application.iam;

public record ScopeIdentityView(String roleCode, Long scopeOrgUnitId, String scopeOrgType, String scopeOrgName) {}
