package com.aubb.server.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RedisRateLimitIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisIntegrationTestSupport.registerRedisProperties(registry);
        registry.add("aubb.redis.rate-limit.policies.login.limit", () -> "2");
        registry.add("aubb.redis.rate-limit.policies.login.window", () -> "PT1M");
        registry.add("aubb.redis.rate-limit.policies.refresh.limit", () -> "1");
        registry.add("aubb.redis.rate-limit.policies.refresh.window", () -> "PT1M");
    }

    @BeforeEach
    void setUp() {
        RedisIntegrationTestSupport.flushAll();
        jdbcTemplate.execute(
                "TRUNCATE TABLE audit_logs, auth_sessions, academic_profiles, user_scope_roles, platform_configs, users, org_units RESTART IDENTITY CASCADE");

        jdbcTemplate.update("""
                INSERT INTO org_units (code, name, type, level, sort_order, status)
                VALUES ('SCH-1', 'AUBB School', 'SCHOOL', 1, 1, 'ACTIVE')
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
    }

    @Test
    void rateLimitsLoginRequestsByClientScope() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("""
                                    {"username":"school-admin","password":"Password123"}
                                    """))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"school-admin","password":"Password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void rateLimitsRefreshRequestsBySessionAndClientScope() throws Exception {
        AuthTokens loginTokens = login("school-admin", "Password123");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(loginTokens.refreshToken())))
                .andExpect(status().isOk())
                .andReturn();

        String rotatedRefreshToken =
                JsonTestSupport.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(rotatedRefreshToken)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private AuthTokens login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return new AuthTokens(
                JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"),
                JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken"));
    }

    private record AuthTokens(String accessToken, String refreshToken) {}
}
