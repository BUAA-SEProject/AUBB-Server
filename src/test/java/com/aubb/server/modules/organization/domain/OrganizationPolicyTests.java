package com.aubb.server.modules.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrganizationPolicyTests {

    private final OrganizationPolicy organizationPolicy = new OrganizationPolicy();

    @Test
    void allowsSchoolToCreateCollege() {
        OrganizationValidationResult result =
                organizationPolicy.validateChild(OrgUnitType.SCHOOL, OrgUnitType.COLLEGE, 1);

        assertThat(result.valid()).isTrue();
        assertThat(result.childLevel()).isEqualTo(2);
    }

    @Test
    void rejectsCourseDirectlyUnderSchool() {
        OrganizationValidationResult result =
                organizationPolicy.validateChild(OrgUnitType.SCHOOL, OrgUnitType.COURSE, 1);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("SCHOOL 下只能创建 COLLEGE 节点");
    }

    @Test
    void rejectsCreatingChildUnderClass() {
        OrganizationValidationResult result = organizationPolicy.validateChild(OrgUnitType.CLASS, OrgUnitType.CLASS, 4);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("CLASS 节点不能再创建下级节点");
    }
}
