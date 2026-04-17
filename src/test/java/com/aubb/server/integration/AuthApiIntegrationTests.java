package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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

class AuthApiIntegrationTests extends AbstractIntegrationTest {

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
                1L,
                "school-admin",
                "School Admin",
                "school-admin@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");
        jdbcTemplate.update("""
                INSERT INTO academic_profiles (user_id, academic_id, real_name, identity_type, profile_status)
                SELECT id, ?, ?, ?, ? FROM users WHERE username = ?
                """, "AUBB-ADMIN-001", "学校管理员", "ADMIN", "ACTIVE", "school-admin");

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
                4L,
                "teacher",
                "Teacher",
                "teacher@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 4L, "CLASS_ADMIN", "teacher");
    }

    @Test
    void rejectsAnonymousAccessToProtectedAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/platform-config/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void logsInWithJwtAndReadsCurrentUserProfile() throws Exception {
        AuthTokens tokens = login("school-admin", "Password123");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("school-admin"))
                .andExpect(jsonPath("$.academicProfile.academicId").value("AUBB-ADMIN-001"))
                .andExpect(jsonPath("$.academicProfile.realName").value("学校管理员"))
                .andExpect(jsonPath("$.identities[0].roleCode").value("SCHOOL_ADMIN"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }

    @Test
    void forbidsClassAdminFromAccessingSchoolLevelPlatformConfigEndpoint() throws Exception {
        AuthTokens tokens = login("teacher", "Password123");

        mockMvc.perform(get("/api/v1/admin/platform-config/current")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void locksAccountAfterFiveFailedLoginAttempts() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("""
                                    {"username":"school-admin","password":"WrongPassword1"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"school-admin","password":"WrongPassword1"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void rejectsDisabledAccountLogin() throws Exception {
        jdbcTemplate.update("UPDATE users SET account_status = 'DISABLED' WHERE username = 'teacher'");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"teacher","password":"Password123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void rejectsExpiredAccountLogin() throws Exception {
        jdbcTemplate.update("""
                UPDATE users
                SET expires_at = now() - interval '1 day'
                WHERE username = 'teacher'
                """);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"teacher","password":"Password123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXPIRED"));
    }

    @Test
    void issuesTwoHourJwtByDefault() throws Exception {
        AuthTokens tokens = login("school-admin", "Password123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"school-admin","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(7200))
                .andExpect(jsonPath("$.refreshExpiresInSeconds")
                        .value(Duration.ofDays(14).toSeconds()))
                .andReturn();

        String token = JsonTestSupport.read(result.getResponse().getContentAsString(), "$.accessToken");
        Jwt jwt = jwtDecoder.decode(token);

        long ttlSeconds =
                jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(7200);
        assertThat(jwt.getClaimAsString("sid")).isNotBlank();
        assertThat(jwt.getClaimAsString("tokenType")).isEqualTo("access");
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void refreshesAccessTokenAndRotatesRefreshToken() throws Exception {
        AuthTokens loginTokens = login("school-admin", "Password123");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(loginTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        String refreshedAccessToken =
                JsonTestSupport.read(refreshResult.getResponse().getContentAsString(), "$.accessToken");
        String rotatedRefreshToken =
                JsonTestSupport.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("school-admin"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(loginTokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        assertThat(rotatedRefreshToken).isNotEqualTo(loginTokens.refreshToken());
    }

    @Test
    void logoutRevokesCurrentAccessAndRefreshToken() throws Exception {
        AuthTokens tokens = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void revokeEndpointInvalidatesSessionBoundTokens() throws Exception {
        AuthTokens tokens = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/auth/revoke")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    private AuthTokens login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        return new AuthTokens(
                JsonTestSupport.read(result.getResponse().getContentAsString(), "$.accessToken"),
                JsonTestSupport.read(result.getResponse().getContentAsString(), "$.refreshToken"));
    }

    private record AuthTokens(String accessToken, String refreshToken) {}
}
