package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthzSchemaIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

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
    void permissionSystemFoundationTablesShouldExistWithBuiltInSeeds() {
        assertThat(countTable("roles")).isEqualTo(1);
        assertThat(countTable("permissions")).isEqualTo(1);
        assertThat(countTable("role_permissions")).isEqualTo(1);
        assertThat(countTable("role_bindings")).isEqualTo(1);
        assertThat(countTable("offering_members")).isEqualTo(1);
        assertThat(countTable("class_members")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select count(*) from roles where is_builtin = true", Integer.class))
                .isGreaterThanOrEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("select count(*) from permissions", Integer.class))
                .isGreaterThanOrEqualTo(40);
        assertThat(jdbcTemplate.queryForObject("select count(*) from role_permissions", Integer.class))
                .isGreaterThan(60);
    }

    @Test
    void auditLogsAndRoleBindingsShouldExposeCompatibilityColumnsAndIndexes() {
        assertThat(loadColumnNames("audit_logs"))
                .contains(
                        "user_id",
                        "resource_type",
                        "resource_id",
                        "scope_type",
                        "scope_id",
                        "decision",
                        "reason",
                        "user_agent");
        assertThat(loadColumnNames("role_bindings"))
                .contains(
                        "constraints_json",
                        "status",
                        "effective_from",
                        "effective_to",
                        "granted_by",
                        "source_type",
                        "source_ref_id");

        assertThat(loadIndexNames("role_bindings"))
                .contains(
                        "ix_role_bindings_user_status",
                        "ix_role_bindings_scope_status",
                        "ix_role_bindings_role_status",
                        "ux_role_bindings_source_scope");
        assertThat(loadIndexNames("role_permissions"))
                .contains("ux_role_permissions_role_permission", "ix_role_permissions_permission_id");
        assertThat(loadIndexNames("audit_logs"))
                .contains(
                        "ix_audit_logs_user_id_created_at",
                        "ix_audit_logs_scope_created_at",
                        "ix_audit_logs_resource_created_at",
                        "ix_audit_logs_decision_created_at");
        assertThat(loadIndexNames("course_members")).contains("ux_course_members_student_active_unique");
    }

    @Test
    void permissionFoundationConstraintsShouldAllowPlatformScope() {
        assertThat(loadConstraintDefinitions("roles"))
                .anySatisfy(definition -> assertThat(definition).contains("'platform'"));
        assertThat(loadConstraintDefinitions("role_bindings"))
                .anySatisfy(definition -> assertThat(definition).contains("'platform'"));
        assertThat(loadConstraintDefinitions("audit_logs"))
                .anySatisfy(definition -> assertThat(definition).contains("'platform'"));
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

    @Test
    void modernTeachingRolesShouldSeedDiscussionAndLabCompatibilityPermissions() {
        assertThat(loadRolePermissions("offering_coordinator")).contains("question_bank.manage");
        assertThat(loadRolePermissions("offering_teacher")).contains("question_bank.manage");
        assertThat(loadRolePermissions("offering_teacher"))
                .contains("discussion.manage", "lab.read", "lab.manage", "lab.report.review");
        assertThat(loadRolePermissions("offering_ta"))
                .contains(
                        "announcement.read",
                        "resource.read",
                        "discussion.participate",
                        "lab.read",
                        "lab.report.review");
        assertThat(loadRolePermissions("class_ta"))
                .contains(
                        "announcement.read",
                        "resource.read",
                        "discussion.participate",
                        "lab.read",
                        "lab.report.review");
        assertThat(loadRolePermissions("student")).contains("discussion.participate", "lab.read");
    }

    private Set<String> loadTemplatePermissions(String templateCode) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT permission_code
                FROM auth_group_template_permissions tp
                JOIN auth_group_templates t ON t.id = tp.template_id
                WHERE t.code = ?
                """, String.class, templateCode));
    }

    private Set<String> loadRolePermissions(String roleCode) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT p.code
                FROM role_permissions rp
                JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE r.code = ?
                """, String.class, roleCode));
    }

    private int countTable(String tableName) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """, Integer.class, tableName);
    }

    private Set<String> loadColumnNames(String tableName) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, tableName));
    }

    private Set<String> loadIndexNames(String tableName) {
        List<String> indexNames = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = ?
                """, String.class, tableName);
        return Set.copyOf(indexNames);
    }

    private List<String> loadConstraintDefinitions(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public'
                  AND t.relname = ?
                """, String.class, tableName);
    }
}
