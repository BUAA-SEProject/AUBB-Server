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

class AuthzJwtSessionIntegrationTests extends AbstractIntegrationTest {

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
        jdbcTemplate.execute(
                "TRUNCATE TABLE audit_logs, auth_sessions, user_scope_roles, platform_configs, users, org_units RESTART IDENTITY CASCADE");

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
        assertThat(jwt.getClaimAsStringList("permissionCodes"))
                .contains("class.manage", "user.manage", "member.manage");
        Number permissionVersion = (Number) jwt.getClaim("permissionVersion");
        assertThat(permissionVersion).isNotNull();
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
