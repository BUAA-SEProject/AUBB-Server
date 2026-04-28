package com.aubb.server.modules.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditDecision;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceType;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResult;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationScope;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationScopePath;
import com.aubb.server.modules.identityaccess.application.authz.core.ResolvedAuthorizationResource;
import com.aubb.server.modules.identityaccess.application.authz.core.ResourceOwnershipResolutionService;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensitiveOperationAuditServiceTests {

    @Mock
    private AuditLogApplicationService auditLogApplicationService;

    @Mock
    private ResourceOwnershipResolutionService resourceOwnershipResolutionService;

    @Test
    void recordAllowedShouldWriteLeafScopeAndPermissionMetadata() {
        SensitiveOperationAuditService service =
                new SensitiveOperationAuditService(auditLogApplicationService, resourceOwnershipResolutionService);
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(1L, "teacher", "Teacher", 1L, AccountStatus.ACTIVE, null, List.of());
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 88L);
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        6L,
                        true,
                        false,
                        null,
                        null,
                        true));

        service.recordAllowed(
                principal,
                AuditAction.SUBMISSION_SOURCE_READ,
                "submission.read_source",
                resourceRef,
                Map.of("channel", "download"));

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogApplicationService)
                .recordDecision(
                        eq(1L),
                        eq(AuditAction.SUBMISSION_SOURCE_READ),
                        eq("SUBMISSION"),
                        eq("88"),
                        eq("class"),
                        eq(101L),
                        eq(AuditDecision.ALLOW),
                        metadataCaptor.capture());
        assertThat(metadataCaptor.getValue())
                .containsEntry("permissionCode", "submission.read_source")
                .containsEntry("reason", "ALLOW")
                .containsEntry("channel", "download");
    }

    @Test
    void recordDeniedShouldIncludeMatchedRolesScopesAndReason() {
        SensitiveOperationAuditService service =
                new SensitiveOperationAuditService(auditLogApplicationService, resourceOwnershipResolutionService);
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                2L, "ta", "Teaching Assistant", 1L, AccountStatus.ACTIVE, null, List.of());
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, 66L);
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        null,
                        true,
                        false,
                        null,
                        null,
                        true));

        AuthorizationResult denied = AuthorizationResult.deny(
                "DENY_SENSITIVE_RESOURCE",
                List.of("offering_teacher"),
                List.of(AuthorizationScope.of(AuthorizationScopeType.OFFERING, 12L)),
                true);

        service.recordDenied(
                principal,
                AuditAction.JUDGE_CONFIG_CHANGE,
                "judge.config",
                resourceRef,
                denied,
                Map.of("operation", "preview"));

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogApplicationService)
                .recordDecision(
                        eq(2L),
                        eq(AuditAction.JUDGE_CONFIG_CHANGE),
                        eq("ASSIGNMENT"),
                        eq("66"),
                        eq("class"),
                        eq(101L),
                        eq(AuditDecision.DENY),
                        metadataCaptor.capture());
        assertThat(metadataCaptor.getValue())
                .containsEntry("permissionCode", "judge.config")
                .containsEntry("reason", "DENY_SENSITIVE_RESOURCE")
                .containsEntry("operation", "preview");
        assertThat(metadataCaptor.getValue().get("matchedRoles")).isEqualTo(List.of("offering_teacher"));
        assertThat(metadataCaptor.getValue().get("matchedScopes")).isEqualTo(List.of("offering:12"));
    }
}
