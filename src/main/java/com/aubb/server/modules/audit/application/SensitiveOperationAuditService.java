package com.aubb.server.modules.audit.application;

import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditDecision;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResult;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationScope;
import com.aubb.server.modules.identityaccess.application.authz.core.ResolvedAuthorizationResource;
import com.aubb.server.modules.identityaccess.application.authz.core.ResourceOwnershipResolutionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensitiveOperationAuditService {

    private final AuditLogApplicationService auditLogApplicationService;
    private final ResourceOwnershipResolutionService resourceOwnershipResolutionService;

    public void recordAllowed(
            AuthenticatedUserPrincipal principal,
            AuditAction action,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            Map<String, Object> metadata) {
        record(
                principal,
                action,
                permissionCode,
                resourceRef,
                AuditDecision.ALLOW,
                "ALLOW",
                List.of(),
                List.of(),
                metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(
            AuthenticatedUserPrincipal principal,
            AuditAction action,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            AuthorizationResult authorizationResult,
            Map<String, Object> metadata) {
        record(
                principal,
                action,
                permissionCode,
                resourceRef,
                AuditDecision.DENY,
                authorizationResult == null ? "DENY" : authorizationResult.reasonCode(),
                authorizationResult == null ? List.of() : authorizationResult.matchedRoles(),
                authorizationResult == null ? List.of() : authorizationResult.matchedScopes(),
                metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(
            AuthenticatedUserPrincipal principal,
            AuditAction action,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            String reasonCode,
            Map<String, Object> metadata) {
        record(
                principal,
                action,
                permissionCode,
                resourceRef,
                AuditDecision.DENY,
                reasonCode,
                List.of(),
                List.of(),
                metadata);
    }

    private void record(
            AuthenticatedUserPrincipal principal,
            AuditAction action,
            String permissionCode,
            AuthorizationResourceRef resourceRef,
            AuditDecision decision,
            String reasonCode,
            List<String> matchedRoles,
            List<AuthorizationScope> matchedScopes,
            Map<String, Object> metadata) {
        ResolvedAuthorizationResource resource = resourceOwnershipResolutionService.resolve(resourceRef);
        AuthorizationScope leafScope = resource.scopePath().leafScope();
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        mergedMetadata.put("permissionCode", permissionCode);
        if (reasonCode != null && !reasonCode.isBlank()) {
            mergedMetadata.put("reason", reasonCode);
        }
        if (matchedRoles != null && !matchedRoles.isEmpty()) {
            mergedMetadata.put("matchedRoles", matchedRoles);
        }
        if (matchedScopes != null && !matchedScopes.isEmpty()) {
            mergedMetadata.put(
                    "matchedScopes",
                    matchedScopes.stream()
                            .map(scope -> scope.type().databaseValue() + ":" + scope.id())
                            .toList());
        }
        if (metadata != null && !metadata.isEmpty()) {
            mergedMetadata.putAll(metadata);
        }
        auditLogApplicationService.recordDecision(
                principal == null ? null : principal.getUserId(),
                action,
                resourceRef.type().name(),
                String.valueOf(resourceRef.id()),
                leafScope.type().databaseValue(),
                leafScope.id(),
                decision,
                mergedMetadata);
    }
}
