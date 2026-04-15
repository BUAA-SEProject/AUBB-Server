package com.aubb.server.modules.identityaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.modules.organization.domain.OrgUnitType;
import org.junit.jupiter.api.Test;

class GovernanceRolePolicyTests {

    private final GovernanceRolePolicy governanceRolePolicy = new GovernanceRolePolicy();

    @Test
    void acceptsClassAdminOnClassScope() {
        RoleScopeValidationResult result =
                governanceRolePolicy.validateScope(GovernanceRole.CLASS_ADMIN, OrgUnitType.CLASS);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsCollegeAdminOnCourseScope() {
        RoleScopeValidationResult result =
                governanceRolePolicy.validateScope(GovernanceRole.COLLEGE_ADMIN, OrgUnitType.COURSE);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("COLLEGE_ADMIN 只能绑定到 COLLEGE 节点");
    }

    @Test
    void rejectsDelegatingHigherPrivilegeFromLowerPrivilegeAdmin() {
        assertThat(governanceRolePolicy.canGrant(GovernanceRole.COURSE_ADMIN, GovernanceRole.COLLEGE_ADMIN))
                .isFalse();
    }
}
