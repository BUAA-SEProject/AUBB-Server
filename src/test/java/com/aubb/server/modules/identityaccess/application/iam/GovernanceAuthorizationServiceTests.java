package com.aubb.server.modules.identityaccess.application.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GovernanceAuthorizationServiceTests {

    @Mock
    private OrgUnitMapper orgUnitMapper;

    @Mock
    private AuthzScopeResolutionService authzScopeResolutionService;

    @Test
    void loadsManageableOrgUnitIdsForAllDescendantsOfVisibleScopes() {
        when(orgUnitMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orgUnit(4L, 2L)))
                .thenReturn(List.of(orgUnit(5L, 4L)))
                .thenReturn(List.of());
        GovernanceAuthorizationService service =
                new GovernanceAuthorizationService(orgUnitMapper, authzScopeResolutionService);
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                10L,
                "college-admin",
                "College Admin",
                2L,
                AccountStatus.ACTIVE,
                null,
                List.of(new ScopeIdentityView("COLLEGE_ADMIN", 2L, "COLLEGE", "Engineering")));

        Set<Long> manageableOrgUnitIds = service.loadManageableOrgUnitIds(principal);

        assertThat(manageableOrgUnitIds).containsExactlyInAnyOrder(2L, 4L, 5L);
    }

    @Test
    void canManageUserAtTraversesAncestorChainInsteadOfLoadingWholeTree() {
        when(orgUnitMapper.selectById(5L)).thenReturn(orgUnit(5L, 4L));
        when(orgUnitMapper.selectById(4L)).thenReturn(orgUnit(4L, 2L));

        GovernanceAuthorizationService service =
                new GovernanceAuthorizationService(orgUnitMapper, authzScopeResolutionService);
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                10L,
                "college-admin",
                "College Admin",
                2L,
                AccountStatus.ACTIVE,
                null,
                List.of(new ScopeIdentityView("COLLEGE_ADMIN", 2L, "COLLEGE", "Engineering")));

        assertThat(service.canManageUserAt(principal, 5L)).isTrue();
        verify(orgUnitMapper, never()).selectList(any());
    }

    @Test
    void builtInGroupBindingShouldGrantGovernanceScopeWithoutLegacyIdentity() {
        when(orgUnitMapper.selectById(5L)).thenReturn(orgUnit(5L, 4L));
        when(orgUnitMapper.selectById(4L)).thenReturn(orgUnit(4L, 2L));

        GovernanceAuthorizationService service =
                new GovernanceAuthorizationService(orgUnitMapper, authzScopeResolutionService);
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                10L,
                "group-college-admin",
                "Group College Admin",
                2L,
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(new GroupBindingView("AUTHZ_GROUP", "college-admin", "COLLEGE", 2L)),
                Set.of(),
                null);

        assertThat(service.canManageUserAt(principal, 5L)).isTrue();
    }

    private OrgUnitEntity orgUnit(Long id, Long parentId) {
        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        return entity;
    }
}
