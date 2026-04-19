package com.aubb.server.modules.identityaccess.application.authz.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.ScopeRef;
import com.aubb.server.modules.identityaccess.application.authz.core.rule.ArchivedAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.rule.OwnerAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.rule.PublishedAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.rule.SensitiveResourceAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.rule.TeachingScopeAbacRule;
import com.aubb.server.modules.identityaccess.application.authz.core.rule.TimeWindowAbacRule;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantRow;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionAuthorizationServiceTests {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-04-18T18:30:00+08:00");

    @Mock
    private RoleBindingGrantQueryMapper roleBindingGrantQueryMapper;

    @Mock
    private ResourceOwnershipResolutionService resourceOwnershipResolutionService;

    @Mock
    private AuthzScopeResolutionService authzScopeResolutionService;

    private PermissionAuthorizationService authorizationService;

    private final AuthenticatedUserPrincipal principal =
            new AuthenticatedUserPrincipal(1L, "student-a", "Student A", 1L, AccountStatus.ACTIVE, null, List.of());

    @BeforeEach
    void setUp() {
        authorizationService = new PermissionAuthorizationService(
                roleBindingGrantQueryMapper,
                resourceOwnershipResolutionService,
                authzScopeResolutionService,
                new DefaultRolePermissionConstraintResolver(),
                List.of(
                        new TeachingScopeAbacRule(),
                        new OwnerAbacRule(),
                        new PublishedAbacRule(),
                        new ArchivedAbacRule(),
                        new TimeWindowAbacRule(),
                        new SensitiveResourceAbacRule()),
                Clock.fixed(Instant.parse("2026-04-18T10:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void authorizeShouldAllowWhenAncestorScopeGrantMatches() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 201L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("submission.read"), any()))
                .thenReturn(List.of(
                        row("offering_teacher", AuthorizationScopeType.OFFERING, 12L, "submission.read", true, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        99L,
                        true,
                        false,
                        null,
                        null,
                        false));

        AuthorizationResult result =
                authorizationService.authorize(principal, "submission.read", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("ALLOW_BY_SCOPE_ROLE");
        assertThat(result.matchedRoles()).containsExactly("offering_teacher");
        assertThat(result.matchedScopes()).containsExactly(AuthorizationScope.of(AuthorizationScopeType.OFFERING, 12L));
        assertThat(result.needAudit()).isTrue();
    }

    @Test
    void authorizeShouldAllowSharedOfferingAssignmentViaClassScopedGrant() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, 301L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(eq(1L), eq("task.read"), any()))
                .thenReturn(List.of(row("student", AuthorizationScopeType.CLASS, 101L, "task.read", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forOffering(1L, 2L, 3L, 12L),
                        1L,
                        true,
                        false,
                        NOW.minusHours(1),
                        NOW.plusDays(1),
                        false));
        when(authzScopeResolutionService.resolveScope(AuthorizationScopeType.CLASS, 101L))
                .thenReturn(new ScopeRef(
                        AuthorizationScopeType.CLASS,
                        101L,
                        List.of(new ScopeRef(AuthorizationScopeType.OFFERING, 12L))));

        AuthorizationResult result =
                authorizationService.authorize(principal, "task.read", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("ALLOW_SHARED_OFFERING_CLASS_SCOPE_COMPAT");
        assertThat(result.matchedScopes()).containsExactly(AuthorizationScope.of(AuthorizationScopeType.CLASS, 101L));
    }

    @Test
    void authorizeShouldDenyWhenScopeDoesNotMatch() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 201L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("submission.read"), any()))
                .thenReturn(List.of(
                        row("offering_teacher", AuthorizationScopeType.OFFERING, 12L, "submission.read", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 13L, 101L),
                        99L,
                        true,
                        false,
                        null,
                        null,
                        false));

        AuthorizationResult result =
                authorizationService.authorize(principal, "submission.read", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DENY_SCOPE_MISMATCH");
    }

    @Test
    void batchAuthorizeShouldReturnPerResourceDecision() {
        AuthorizationResourceRef allowedRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 201L);
        AuthorizationResourceRef deniedRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 202L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("submission.read"), any()))
                .thenReturn(List.of(
                        row("offering_teacher", AuthorizationScopeType.OFFERING, 12L, "submission.read", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(allowedRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        allowedRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        99L,
                        true,
                        false,
                        null,
                        null,
                        false));
        when(resourceOwnershipResolutionService.resolve(deniedRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        deniedRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 13L, 102L),
                        99L,
                        true,
                        false,
                        null,
                        null,
                        false));

        BatchAuthorizationResult result = authorizationService.batchAuthorize(
                principal, "submission.read", List.of(allowedRef, deniedRef), AuthorizationContext.of(NOW));

        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(allowedRef).allowed()).isTrue();
        assertThat(result.results().get(deniedRef).allowed()).isFalse();
    }

    @Test
    void buildScopeFilterShouldExposeDefaultStudentConstraints() {
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("grade.read"), any()))
                .thenReturn(List.of(row("student", AuthorizationScopeType.CLASS, 101L, "grade.read", false, "{}")));

        AuthorizationScopeFilter filter =
                authorizationService.buildScopeFilter(principal, "grade.read", AuthorizationContext.of(NOW));

        assertThat(filter.permissionCode()).isEqualTo("grade.read");
        assertThat(filter.clauses()).hasSize(1);
        assertThat(filter.clauses().getFirst().scope())
                .isEqualTo(AuthorizationScope.of(AuthorizationScopeType.CLASS, 101L));
        assertThat(filter.clauses().getFirst().constraints().ownerOnly()).isTrue();
        assertThat(filter.clauses().getFirst().constraints().publishedOnly()).isTrue();
    }

    @Test
    void ownerOnlyRuleShouldDenyWhenUserIsNotOwner() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 201L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("submission.read"), any()))
                .thenReturn(
                        List.of(row("student", AuthorizationScopeType.CLASS, 101L, "submission.read", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        2L,
                        true,
                        false,
                        null,
                        null,
                        false));

        AuthorizationResult result =
                authorizationService.authorize(principal, "submission.read", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DENY_NOT_OWNER");
    }

    @Test
    void publishedOnlyRuleShouldDenyWhenResourceIsNotPublished() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, 301L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(eq(1L), eq("task.read"), any()))
                .thenReturn(List.of(row("student", AuthorizationScopeType.CLASS, 101L, "task.read", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        null,
                        false,
                        false,
                        null,
                        null,
                        false));

        AuthorizationResult result =
                authorizationService.authorize(principal, "task.read", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DENY_NOT_PUBLISHED");
    }

    @Test
    void archivedWriteRuleShouldDenyTeachingWriteOperation() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, 301L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(eq(1L), eq("task.edit"), any()))
                .thenReturn(List.of(
                        row("offering_teacher", AuthorizationScopeType.OFFERING, 12L, "task.edit", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forOffering(1L, 2L, 3L, 12L),
                        null,
                        true,
                        true,
                        null,
                        null,
                        false));

        AuthorizationResult result =
                authorizationService.authorize(principal, "task.edit", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DENY_ARCHIVED_READ_ONLY");
    }

    @Test
    void timeWindowRuleShouldDenyStudentSubmitOutsideWindow() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.ASSIGNMENT, 301L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("ide.submit"), any()))
                .thenReturn(List.of(row("student", AuthorizationScopeType.CLASS, 101L, "ide.submit", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        1L,
                        true,
                        false,
                        NOW.plusHours(2),
                        NOW.plusDays(1),
                        false));

        AuthorizationResult result =
                authorizationService.authorize(principal, "ide.submit", resourceRef, AuthorizationContext.of(NOW));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DENY_OUTSIDE_TIME_WINDOW");
    }

    @Test
    void sensitiveResourceRuleShouldRequireExplicitSensitivePermission() {
        AuthorizationResourceRef resourceRef = new AuthorizationResourceRef(AuthorizationResourceType.SUBMISSION, 201L);
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserIdAndPermissionCode(
                        eq(1L), eq("submission.read"), any()))
                .thenReturn(List.of(
                        row("offering_teacher", AuthorizationScopeType.OFFERING, 12L, "submission.read", false, "{}")));
        when(resourceOwnershipResolutionService.resolve(resourceRef))
                .thenReturn(new ResolvedAuthorizationResource(
                        resourceRef,
                        AuthorizationScopePath.forClass(1L, 2L, 3L, 12L, 101L),
                        99L,
                        true,
                        false,
                        null,
                        null,
                        false));

        AuthorizationResult result = authorizationService.authorize(
                principal,
                "submission.read",
                resourceRef,
                AuthorizationContext.of(NOW).withSensitiveAccess(true));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DENY_SENSITIVE_RESOURCE");
    }

    private RoleBindingGrantRow row(
            String roleCode,
            AuthorizationScopeType scopeType,
            Long scopeId,
            String permissionCode,
            boolean permissionSensitive,
            String constraintsJson) {
        RoleBindingGrantRow row = new RoleBindingGrantRow();
        row.setBindingId(900L);
        row.setRoleId(800L);
        row.setRoleCode(roleCode);
        row.setRoleName(roleCode);
        row.setRoleCategory("TEACHING");
        row.setRoleScopeType(scopeType.databaseValue());
        row.setPermissionId(700L);
        row.setPermissionCode(permissionCode);
        row.setPermissionResourceType(permissionCode.split("\\.")[0]);
        row.setPermissionAction(permissionCode.substring(permissionCode.indexOf('.') + 1));
        row.setPermissionDescription(permissionCode);
        row.setPermissionSensitive(permissionSensitive);
        row.setBindingScopeType(scopeType.databaseValue());
        row.setBindingScopeId(scopeId);
        row.setConstraintsJson(constraintsJson);
        row.setGrantedBy(null);
        return row;
    }
}
