package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PermissionAuthorizationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RoleBindingGrantQueryMapper roleBindingGrantQueryMapper;
    private final ResourceOwnershipResolutionService resourceOwnershipResolutionService;
    private final DefaultRolePermissionConstraintResolver defaultRolePermissionConstraintResolver;
    private final List<AuthorizationAbacRule> authorizationAbacRules;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public PermissionAuthorizationService(
            RoleBindingGrantQueryMapper roleBindingGrantQueryMapper,
            ResourceOwnershipResolutionService resourceOwnershipResolutionService,
            DefaultRolePermissionConstraintResolver defaultRolePermissionConstraintResolver,
            List<AuthorizationAbacRule> authorizationAbacRules) {
        this(
                roleBindingGrantQueryMapper,
                resourceOwnershipResolutionService,
                defaultRolePermissionConstraintResolver,
                authorizationAbacRules,
                Clock.systemUTC());
    }

    PermissionAuthorizationService(
            RoleBindingGrantQueryMapper roleBindingGrantQueryMapper,
            ResourceOwnershipResolutionService resourceOwnershipResolutionService,
            DefaultRolePermissionConstraintResolver defaultRolePermissionConstraintResolver,
            List<AuthorizationAbacRule> authorizationAbacRules,
            Clock clock) {
        this.roleBindingGrantQueryMapper = roleBindingGrantQueryMapper;
        this.resourceOwnershipResolutionService = resourceOwnershipResolutionService;
        this.defaultRolePermissionConstraintResolver = defaultRolePermissionConstraintResolver;
        this.authorizationAbacRules = List.copyOf(authorizationAbacRules);
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public AuthorizationResult authorize(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            AuthorizationContext context) {
        ResolvedAuthorizationResource resource = resourceOwnershipResolutionService.resolve(resourceRef);
        List<RoleBindingGrant> grants = loadGrants(principal, permissionCode, context);
        if (grants.isEmpty()) {
            return AuthorizationResult.deny("DENY_NO_ROLE_BINDING", List.of(), List.of(), false);
        }
        List<RoleBindingGrant> scopeMatchedGrants = grants.stream()
                .filter(grant -> resource.scopePath().isCoveredBy(grant.scope()))
                .toList();
        if (scopeMatchedGrants.isEmpty()) {
            return AuthorizationResult.deny(
                    "DENY_SCOPE_MISMATCH", roleCodes(grants), scopes(grants), needsAudit(grants, context, resource));
        }
        List<String> denyReasons = new ArrayList<>();
        for (RoleBindingGrant grant : scopeMatchedGrants) {
            RoleBindingConstraints constraints = mergeConstraints(grant);
            Optional<String> denyReason = evaluateRules(principal, grant, resource, context, constraints);
            if (denyReason.isEmpty()) {
                return AuthorizationResult.allow(
                        "ALLOW_BY_SCOPE_ROLE",
                        List.of(grant.role().code()),
                        List.of(grant.scope()),
                        needsAudit(List.of(grant), context, resource));
            }
            denyReasons.add(denyReason.get());
        }
        return AuthorizationResult.deny(
                denyReasons.stream().findFirst().orElse("DENY_RULES_NOT_SATISFIED"),
                roleCodes(scopeMatchedGrants),
                scopes(scopeMatchedGrants),
                needsAudit(scopeMatchedGrants, context, resource));
    }

    @Transactional(readOnly = true)
    public BatchAuthorizationResult batchAuthorize(
            AuthenticatedUserPrincipal principal,
            String permissionCode,
            List<AuthorizationResourceRef> resourceRefs,
            AuthorizationContext context) {
        Map<AuthorizationResourceRef, AuthorizationResult> results = new LinkedHashMap<>();
        for (AuthorizationResourceRef resourceRef : resourceRefs) {
            results.put(resourceRef, authorize(principal, permissionCode, resourceRef, context));
        }
        return new BatchAuthorizationResult(results);
    }

    @Transactional(readOnly = true)
    public AuthorizationScopeFilter buildScopeFilter(
            AuthenticatedUserPrincipal principal, String permissionCode, AuthorizationContext context) {
        List<RoleBindingGrant> grants = loadGrants(principal, permissionCode, context);
        List<AuthorizationScopeFilterClause> clauses = grants.stream()
                .map(grant -> new AuthorizationScopeFilterClause(
                        grant.scope(), Set.of(grant.role().code()), mergeConstraints(grant)))
                .toList();
        return new AuthorizationScopeFilter(permissionCode, clauses);
    }

    private List<RoleBindingGrant> loadGrants(
            AuthenticatedUserPrincipal principal, String permissionCode, AuthorizationContext context) {
        OffsetDateTime asOf =
                context == null ? OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)) : context.requestTime();
        return roleBindingGrantQueryMapper
                .selectActiveGrantRowsByUserIdAndPermissionCode(principal.getUserId(), permissionCode, asOf)
                .stream()
                .map(this::toGrant)
                .toList();
    }

    private RoleBindingGrant toGrant(RoleBindingGrantRow row) {
        PermissionDefinition permission = new PermissionDefinition(
                row.getPermissionId(),
                row.getPermissionCode(),
                row.getPermissionResourceType(),
                row.getPermissionAction(),
                row.getPermissionDescription(),
                Boolean.TRUE.equals(row.getPermissionSensitive()));
        RoleDefinition role = new RoleDefinition(
                row.getRoleId(),
                row.getRoleCode(),
                row.getRoleName(),
                row.getRoleDescription(),
                row.getRoleCategory(),
                AuthorizationScopeType.fromDatabaseValue(row.getRoleScopeType()),
                Boolean.TRUE.equals(row.getRoleBuiltin()),
                row.getRoleStatus());
        RoleBindingDefinition binding = new RoleBindingDefinition(
                row.getBindingId(),
                row.getUserId(),
                row.getBindingRoleId(),
                AuthorizationScope.of(
                        AuthorizationScopeType.fromDatabaseValue(row.getBindingScopeType()), row.getBindingScopeId()),
                parseConstraints(row.getConstraintsJson()),
                row.getBindingStatus(),
                row.getGrantedBy(),
                row.getSourceType(),
                row.getSourceRefId());
        return new RoleBindingGrant(role, permission, binding);
    }

    private RoleBindingConstraints parseConstraints(String constraintsJson) {
        if (constraintsJson == null || constraintsJson.isBlank()) {
            return RoleBindingConstraints.none();
        }
        try {
            return RoleBindingConstraints.fromMap(objectMapper.readValue(constraintsJson, MAP_TYPE));
        } catch (Exception ex) {
            log.warn("parse_role_binding_constraints_failed constraintsJson={}", constraintsJson, ex);
            return RoleBindingConstraints.none();
        }
    }

    private RoleBindingConstraints mergeConstraints(RoleBindingGrant grant) {
        return defaultRolePermissionConstraintResolver
                .resolve(grant.role().code(), grant.permission())
                .merge(grant.binding().constraints());
    }

    private Optional<String> evaluateRules(
            AuthenticatedUserPrincipal principal,
            RoleBindingGrant grant,
            ResolvedAuthorizationResource resource,
            AuthorizationContext context,
            RoleBindingConstraints constraints) {
        for (AuthorizationAbacRule authorizationAbacRule : authorizationAbacRules) {
            Optional<String> denyReason =
                    authorizationAbacRule.evaluate(principal, grant, resource, context, constraints);
            if (denyReason.isPresent()) {
                return denyReason;
            }
        }
        return Optional.empty();
    }

    private List<String> roleCodes(List<RoleBindingGrant> grants) {
        LinkedHashSet<String> roleCodes = new LinkedHashSet<>();
        grants.forEach(grant -> roleCodes.add(grant.role().code()));
        return List.copyOf(roleCodes);
    }

    private List<AuthorizationScope> scopes(List<RoleBindingGrant> grants) {
        LinkedHashSet<AuthorizationScope> scopes = new LinkedHashSet<>();
        grants.forEach(grant -> scopes.add(grant.scope()));
        return List.copyOf(scopes);
    }

    private boolean needsAudit(
            List<RoleBindingGrant> grants, AuthorizationContext context, ResolvedAuthorizationResource resource) {
        return grants.stream().anyMatch(grant -> grant.permission().sensitive())
                || context.sensitiveAccess()
                || resource.sensitive();
    }
}
