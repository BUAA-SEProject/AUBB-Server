package com.aubb.server.modules.identityaccess.application.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
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

    @Test
    void loadsManageableOrgUnitIdsForAllDescendantsOfVisibleScopes() {
        when(orgUnitMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        List.of(orgUnit(1L, null), orgUnit(2L, 1L), orgUnit(3L, 1L), orgUnit(4L, 2L), orgUnit(5L, 4L)));
        GovernanceAuthorizationService service = new GovernanceAuthorizationService(orgUnitMapper);
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

    private OrgUnitEntity orgUnit(Long id, Long parentId) {
        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        return entity;
    }
}
