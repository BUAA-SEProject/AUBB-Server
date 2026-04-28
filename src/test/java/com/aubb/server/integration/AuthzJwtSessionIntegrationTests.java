package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthzJwtSessionIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisIntegrationTestSupport.registerRedisProperties(registry);
    }

    @BeforeEach
    void setUp() {
        RedisIntegrationTestSupport.flushAll();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    auth_sessions,
                    role_bindings,
                    teaching_classes,
                    course_offering_college_maps,
                    course_offerings,
                    academic_terms,
                    course_catalogs,
                    user_scope_roles,
                    platform_configs,
                    users,
                    org_units
                RESTART IDENTITY CASCADE
                """);

        jdbcTemplate.update("""
                INSERT INTO org_units (code, name, type, level, sort_order, status)
                VALUES ('SCH-1', 'AUBB School', 'SCHOOL', 1, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (1, 'COL-1', 'Engineering', 'COLLEGE', 2, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (2, 'CRS-1', 'Software Engineering', 'COURSE', 3, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (3, 'CLS-1', 'SE Class 1', 'CLASS', 4, 1, 'ACTIVE')
                """);

        insertUser(1L, "school-admin", "School Admin", "school-admin@example.com");
        insertUser(4L, "teacher", "Teacher", "teacher@example.com");

        jdbcTemplate.update("""
                INSERT INTO academic_terms (
                    term_code,
                    term_name,
                    school_year,
                    semester,
                    start_date,
                    end_date
                ) VALUES ('2026-SPRING', '2026 春季学期', '2025-2026', 'SPRING', '2026-02-20', '2026-07-10')
                """);
        jdbcTemplate.update("""
                INSERT INTO course_catalogs (
                    course_code,
                    course_name,
                    course_type,
                    credit,
                    total_hours,
                    department_unit_id,
                    description
                ) VALUES ('SE101', '软件工程', 'REQUIRED', 3.0, 48, 2, '核心课程')
                """);
        jdbcTemplate.update("""
                INSERT INTO course_offerings (
                    catalog_id,
                    term_id,
                    offering_code,
                    offering_name,
                    primary_college_unit_id,
                    org_course_unit_id,
                    delivery_mode,
                    language,
                    capacity,
                    created_by_user_id
                ) VALUES (1, 1, 'SE101-2026SP-01', '软件工程（2026春）', 2, 3, 'HYBRID', 'ZH', 120, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO teaching_classes (
                    offering_id,
                    class_code,
                    class_name,
                    entry_year,
                    org_class_unit_id,
                    capacity
                ) VALUES (1, 'CLS-2026-01', 'SE Class 1', 2026, 4, 60)
                """);

        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 4L, "CLASS_ADMIN", "teacher");
    }

    @Test
    void loginTokenShouldCarryGroupBindingsAndPermissionSnapshot() throws Exception {
        AuthTokens tokens = loginWithRefresh("teacher", "Password123");

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) jwt.getClaim("groupBindings");

        assertThat(bindings).extracting(binding -> binding.get("templateCode")).contains("class-admin");
        assertThat(bindings).extracting(binding -> binding.get("scopeType")).contains("CLASS");
        assertThat(bindings).anySatisfy(binding -> {
            assertThat(binding.get("templateCode")).isEqualTo("class-admin");
            assertThat(((Number) binding.get("scopeRefId")).longValue()).isEqualTo(1L);
        });
        assertThat(jwt.getClaimAsStringList("permissionCodes"))
                .contains("class.manage", "member.manage", "role_binding.manage");
        Number permissionVersion = (Number) jwt.getClaim("permissionVersion");
        assertThat(permissionVersion).isNotNull();
        assertThat(jwt.getClaimAsBoolean("roleBindingSnapshot")).isTrue();
    }

    @Test
    void loginTokenShouldUseRoleBindingSnapshotWhenLegacyGovernanceCreatedAtIsSlightlyFuture() throws Exception {
        jdbcTemplate.update("DELETE FROM user_scope_roles WHERE user_id = ?", 2L);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (
                    user_id,
                    scope_org_unit_id,
                    role_code,
                    created_at
                ) VALUES (?, ?, ?, clock_timestamp() + interval '5 second')
                """, 2L, 4L, "CLASS_ADMIN");

        AuthTokens tokens = loginWithRefresh("teacher", "Password123");

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());

        assertThat(jwt.getClaimAsBoolean("roleBindingSnapshot")).isTrue();
        assertThat(jwt.getClaimAsStringList("permissionCodes")).contains("class.manage", "role_binding.manage");
    }

    @Test
    void loginTokenShouldUseRoleBindingSnapshotWhenLegacyAuthzGroupJoinedAtIsSlightlyFuture() throws Exception {
        jdbcTemplate.update("DELETE FROM user_scope_roles WHERE user_id = ?", 2L);
        Long templateId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_group_templates WHERE code = ?", Long.class, "class-admin");
        jdbcTemplate.update("""
                INSERT INTO auth_groups (
                    template_id,
                    scope_type,
                    scope_ref_id,
                    display_name,
                    managed_by_system,
                    status
                ) VALUES (?, 'CLASS', ?, 'class-admin-future-join', FALSE, 'ACTIVE')
                """, templateId, 1L);
        Long groupId =
                jdbcTemplate.queryForObject("SELECT currval(pg_get_serial_sequence('auth_groups', 'id'))", Long.class);
        jdbcTemplate.update("""
                INSERT INTO auth_group_members (
                    group_id,
                    user_id,
                    source_type,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
                    ?,
                    'MANUAL',
                    clock_timestamp() + interval '5 second',
                    clock_timestamp() + interval '5 second',
                    clock_timestamp() + interval '5 second'
                )
                """, groupId, 2L);

        AuthTokens tokens = loginWithRefresh("teacher", "Password123");

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());

        assertThat(jwt.getClaimAsBoolean("roleBindingSnapshot")).isTrue();
        assertThat(jwt.getClaimAsStringList("permissionCodes")).contains("class.manage", "role_binding.manage");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) jwt.getClaim("groupBindings");
        assertThat(bindings).anySatisfy(binding -> {
            assertThat(binding.get("templateCode")).isEqualTo("class-admin");
            assertThat(binding.get("source")).isEqualTo("AUTHZ_GROUP");
        });
    }

    @Test
    void loginTokenShouldLoadAuthoritiesFromRoleBindingsWithoutLegacyScopeRoles() throws Exception {
        jdbcTemplate.update("DELETE FROM user_scope_roles WHERE user_id = ?", 1L);
        jdbcTemplate.update("""
                INSERT INTO role_bindings (
                    user_id,
                    role_id,
                    scope_type,
                    scope_id,
                    constraints_json,
                    status,
                    source_type,
                    source_ref_id
                )
                SELECT ?, id, 'school', ?, '{}'::jsonb, 'ACTIVE', 'MANUAL', ?
                FROM roles
                WHERE code = 'school_admin'
                """, 1L, 1L, 10001L);

        AuthTokens tokens = loginWithRefresh("school-admin", "Password123");

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) jwt.getClaim("groupBindings");

        assertThat(bindings).anySatisfy(binding -> {
            assertThat(binding.get("templateCode")).isEqualTo("school-admin");
            assertThat(binding.get("scopeType")).isEqualTo("SCHOOL");
            assertThat(((Number) binding.get("scopeRefId")).longValue()).isEqualTo(1L);
        });
        assertThat(jwt.getClaimAsStringList("permissionCodes"))
                .contains("school.manage", "role_binding.manage", "report.export");
        assertThat(jwt.getClaimAsStringList("authorities")).contains("SCHOOL_ADMIN");
        assertThat(jwt.getClaimAsBoolean("roleBindingSnapshot")).isTrue();
    }

    @Test
    void loginTokenShouldUseRoleBindingSnapshotWhenManualRoleBindingStartsWithinClockSkewTolerance() throws Exception {
        jdbcTemplate.update("DELETE FROM user_scope_roles WHERE user_id = ?", 2L);
        jdbcTemplate.update("""
                INSERT INTO role_bindings (
                    user_id,
                    role_id,
                    scope_type,
                    scope_id,
                    constraints_json,
                    status,
                    effective_from,
                    effective_to,
                    granted_by,
                    source_type,
                    source_ref_id
                )
                SELECT ?, id, 'class', ?, '{}'::jsonb, 'ACTIVE', clock_timestamp() + interval '900 milliseconds', NULL, NULL, 'MANUAL', ?
                FROM roles
                WHERE code = 'class_admin'
                """, 2L, 1L, 20001L);

        AuthTokens tokens = loginWithRefresh("teacher", "Password123");

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());

        assertThat(jwt.getClaimAsBoolean("roleBindingSnapshot")).isTrue();
        assertThat(jwt.getClaimAsStringList("permissionCodes")).contains("class.manage", "role_binding.manage");
    }

    @Test
    void revokingSessionsShouldInvalidateOldAccessTokenWithPermissionSnapshot() throws Exception {
        String schoolAdminToken =
                loginWithRefresh("school-admin", "Password123").accessToken();
        AuthTokens teacherTokens = loginWithRefresh("teacher", "Password123");

        mockMvc.perform(post("/api/v1/admin/users/2/sessions/revoke")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"reason":"AUTHZ_GROUP_CHANGED"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + teacherTokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    private void insertUser(Long primaryOrgUnitId, String username, String displayName, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    primary_org_unit_id,
                    username,
                    display_name,
                    email,
                    password_hash,
                    account_status,
                    failed_login_attempts
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                primaryOrgUnitId,
                username,
                displayName,
                email,
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
    }

    private AuthTokens loginWithRefresh(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andReturn();
        return new AuthTokens(
                JsonTestSupport.read(result.getResponse().getContentAsString(), "$.accessToken"),
                JsonTestSupport.read(result.getResponse().getContentAsString(), "$.refreshToken"));
    }

    private record AuthTokens(String accessToken, String refreshToken) {}
}
