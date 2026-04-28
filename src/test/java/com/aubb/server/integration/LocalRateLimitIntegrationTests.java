package com.aubb.server.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

class LocalRateLimitIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void rateLimitProperties(DynamicPropertyRegistry registry) {
        registry.add("aubb.redis.enabled", () -> "false");
        registry.add("aubb.redis.rate-limit.enabled", () -> "true");
        registry.add("aubb.redis.rate-limit.policies.login.limit", () -> "1");
        registry.add("aubb.redis.rate-limit.policies.login.window", () -> "PT1M");
    }

    @BeforeEach
    void setUp() {
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
    void loginEndpointStillRateLimitedWhenRedisEnhancementDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"school-admin","password":"Password123"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"school-admin","password":"Password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
