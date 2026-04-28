package com.aubb.server.modules.identityaccess.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aubb.server.modules.identityaccess.application.authz.AuthzScopeResolutionService;
import com.aubb.server.modules.identityaccess.application.authz.GroupBindingView;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityService;
import com.aubb.server.modules.identityaccess.application.iam.ScopeIdentityView;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupGrantRow;
import com.aubb.server.modules.identityaccess.infrastructure.authz.AuthzGroupQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantQueryMapper;
import com.aubb.server.modules.identityaccess.infrastructure.permission.RoleBindingGrantRow;
import com.aubb.server.modules.identityaccess.infrastructure.profile.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticatedPrincipalLoaderTests {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AcademicProfileMapper academicProfileMapper;

    @Mock
    private ScopeIdentityService scopeIdentityService;

    @Mock
    private AuthzGroupQueryMapper authzGroupQueryMapper;

    @Mock
    private AuthzScopeResolutionService authzScopeResolutionService;

    @Mock
    private RoleBindingGrantQueryMapper roleBindingGrantQueryMapper;

    private AuthenticatedPrincipalLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AuthenticatedPrincipalLoader(
                userMapper,
                academicProfileMapper,
                scopeIdentityService,
                authzGroupQueryMapper,
                authzScopeResolutionService,
                roleBindingGrantQueryMapper);
    }

    @Test
    void loadPrincipalShouldPreferRoleBindingsForPermissionSnapshotAndAuthorities() {
        when(userMapper.selectById(1L)).thenReturn(activeUser(1L, "mixed-user", "Mixed User"));
        when(scopeIdentityService.loadForUser(1L))
                .thenReturn(List.of(new ScopeIdentityView("SCHOOL_ADMIN", 1L, "SCHOOL", "AUBB School")));
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserId(eq(1L), any()))
                .thenReturn(List.of(
                        roleBindingRow(101L, "offering_teacher", "offering", 12L, "submission.read", "ROLE_BINDING"),
                        roleBindingRow(
                                101L, "offering_teacher", "offering", 12L, "submission.read_source", "ROLE_BINDING"),
                        roleBindingRow(102L, "class_admin", "class", 9001L, "class.manage", "ROLE_BINDING")));
        when(authzGroupQueryMapper.selectActiveGrantRowsByUserId(1L))
                .thenReturn(List.of(authzGroupGrantRow(501L, "grade-corrector", "OFFERING", 12L, "grade.override")));
        when(authzScopeResolutionService.findTeachingClassIdByOrgClassUnitId(9001L))
                .thenReturn(301L);

        AuthenticatedUserPrincipal principal = loader.loadPrincipal(1L);

        assertThat(principal).isNotNull();
        assertThat(principal.getGroupBindings())
                .containsExactly(
                        new GroupBindingView("ROLE_BINDING", "offering-instructor", "OFFERING", 12L),
                        new GroupBindingView("ROLE_BINDING", "class-admin", "CLASS", 301L),
                        new GroupBindingView("AUTHZ_GROUP", "grade-corrector", "OFFERING", 12L));
        assertThat(principal.getPermissionCodes())
                .contains("class.manage", "grade.override", "submission.read", "submission.read_source");
        assertThat(principal.hasAuthority("CLASS_ADMIN")).isTrue();
        assertThat(principal.hasAuthority("SCHOOL_ADMIN")).isFalse();
        assertThat(principal.isRoleBindingSnapshot()).isTrue();
    }

    @Test
    void loadPrincipalShouldNotReviveLegacyPermissionsWhenRoleBindingsAreMissing() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, "legacy-admin", "Legacy Admin"));
        when(scopeIdentityService.loadForUser(2L))
                .thenReturn(List.of(new ScopeIdentityView("SCHOOL_ADMIN", 1L, "SCHOOL", "AUBB School")));
        when(roleBindingGrantQueryMapper.selectActiveGrantRowsByUserId(eq(2L), any()))
                .thenReturn(List.of());
        when(authzGroupQueryMapper.selectActiveGrantRowsByUserId(2L)).thenReturn(List.of());

        AuthenticatedUserPrincipal principal = loader.loadPrincipal(2L);

        assertThat(principal).isNotNull();
        assertThat(principal.getGroupBindings()).isEmpty();
        assertThat(principal.getPermissionCodes()).isEmpty();
        assertThat(principal.hasAuthority("SCHOOL_ADMIN")).isFalse();
        assertThat(principal.isRoleBindingSnapshot()).isFalse();
        verifyNoInteractions(authzScopeResolutionService);
    }

    private UserEntity activeUser(Long userId, String username, String displayName) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setPrimaryOrgUnitId(1L);
        entity.setUsername(username);
        entity.setDisplayName(displayName);
        entity.setAccountStatus(AccountStatus.ACTIVE.name());
        return entity;
    }

    private RoleBindingGrantRow roleBindingRow(
            Long bindingId, String roleCode, String scopeType, Long scopeId, String permissionCode, String sourceType) {
        RoleBindingGrantRow row = new RoleBindingGrantRow();
        row.setBindingId(bindingId);
        row.setRoleCode(roleCode);
        row.setBindingScopeType(scopeType);
        row.setBindingScopeId(scopeId);
        row.setPermissionCode(permissionCode);
        row.setSourceType(sourceType);
        return row;
    }

    private AuthzGroupGrantRow authzGroupGrantRow(
            Long groupId, String templateCode, String scopeType, Long scopeRefId, String permissionCode) {
        AuthzGroupGrantRow row = new AuthzGroupGrantRow();
        row.setGroupId(groupId);
        row.setTemplateCode(templateCode);
        row.setScopeType(scopeType);
        row.setScopeRefId(scopeRefId);
        row.setPermissionCode(permissionCode);
        return row;
    }
}
