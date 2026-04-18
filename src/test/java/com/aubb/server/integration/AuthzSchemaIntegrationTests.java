package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthzSchemaIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authzSchemaShouldExposeBuiltInTemplatesAndPermissions() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_permission_defs", Integer.class))
                .isGreaterThan(20);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from auth_group_templates where built_in = true", Integer.class))
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    void builtInTemplatePermissionsShouldStayAlignedWithLegacyExpectations() {
        assertThat(loadTemplatePermissions("school-admin")).contains("audit.read", "audit.source.read");
        assertThat(loadTemplatePermissions("college-admin")).contains("user.session.revoke");
        assertThat(loadTemplatePermissions("class-admin"))
                .contains("user.read", "user.manage", "user.membership.manage", "user.session.revoke");
        assertThat(loadTemplatePermissions("offering-instructor"))
                .contains("offering.manage", "class.manage", "judge.profile.manage");
        assertThat(loadTemplatePermissions("observer"))
                .containsExactlyInAnyOrder("offering.read", "class.read", "assignment.read");
    }

    private Set<String> loadTemplatePermissions(String templateCode) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT permission_code
                FROM auth_group_template_permissions tp
                JOIN auth_group_templates t ON t.id = tp.template_id
                WHERE t.code = ?
                """, String.class, templateCode));
    }
}
