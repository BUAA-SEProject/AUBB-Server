package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RedisProtectedEndpointRateLimitIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisIntegrationTestSupport.registerRedisProperties(registry);
        registry.add("aubb.redis.rate-limit.policies.sample-run.limit", () -> "1");
        registry.add("aubb.redis.rate-limit.policies.sample-run.window", () -> "PT1M");
        registry.add("aubb.redis.rate-limit.policies.submission-create.limit", () -> "1");
        registry.add("aubb.redis.rate-limit.policies.submission-create.window", () -> "PT1M");
        registry.add("aubb.redis.rate-limit.policies.submission-artifact-upload.limit", () -> "1");
        registry.add("aubb.redis.rate-limit.policies.submission-artifact-upload.window", () -> "PT1M");
        registry.add("aubb.redis.rate-limit.policies.lab-attachment-upload.limit", () -> "1");
        registry.add("aubb.redis.rate-limit.policies.lab-attachment-upload.window", () -> "PT1M");
    }

    @BeforeEach
    void setUp() {
        RedisIntegrationTestSupport.flushAll();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    lab_report_attachments,
                    lab_reports,
                    labs,
                    programming_sample_runs,
                    programming_workspaces,
                    programming_workspace_revisions,
                    submission_answers,
                    submission_artifacts,
                    submissions,
                    question_bank_question_tags,
                    question_bank_tags,
                    question_bank_question_options,
                    question_bank_questions,
                    question_bank_categories,
                    assignment_question_options,
                    assignment_questions,
                    assignment_sections,
                    assignments,
                    auth_sessions,
                    course_members,
                    teaching_classes,
                    course_offering_college_maps,
                    course_offerings,
                    academic_terms,
                    course_catalogs,
                    user_org_memberships,
                    academic_profiles,
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
                "student-a",
                "Student A",
                "student-a@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
        jdbcTemplate.update("""
                INSERT INTO academic_profiles (user_id, academic_id, real_name, identity_type, profile_status)
                SELECT id, ?, ?, ?, ? FROM users WHERE username = ?
                """, "S-2026", "学生A", "STUDENT", "ACTIVE", "student-a");
    }

    @Test
    void rateLimitsProgrammingSampleRunEndpoint() throws Exception {
        String studentToken = login("student-a", "Password123");

        assertClientFailureWithoutRateLimit(mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                999L,
                                888L)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("{}"))
                .andReturn());

        mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                999L,
                                888L)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void rateLimitsSubmissionCreateEndpoint() throws Exception {
        String studentToken = login("student-a", "Password123");

        assertClientFailureWithoutRateLimit(
                mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", 999L)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {"contentText":"第一次提交"}
                                """))
                        .andReturn());

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", 999L)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {"contentText":"第二次提交"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void rateLimitsSubmissionArtifactUploadEndpoint() throws Exception {
        String studentToken = login("student-a", "Password123");
        MockMultipartFile file = new MockMultipartFile("file", "answer.txt", "text/plain", "hello".getBytes());

        assertClientFailureWithoutRateLimit(
                mockMvc.perform(multipart("/api/v1/me/assignments/{assignmentId}/submission-artifacts", 999L)
                                .file(file)
                                .header("Authorization", "Bearer " + studentToken))
                        .andReturn());

        mockMvc.perform(multipart("/api/v1/me/assignments/{assignmentId}/submission-artifacts", 999L)
                        .file(new MockMultipartFile("file", "answer.txt", "text/plain", "hello".getBytes()))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void rateLimitsLabAttachmentUploadEndpoint() throws Exception {
        String studentToken = login("student-a", "Password123");
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", "lab".getBytes());

        assertClientFailureWithoutRateLimit(mockMvc.perform(multipart("/api/v1/me/labs/{labId}/attachments", 999L)
                        .file(file)
                        .header("Authorization", "Bearer " + studentToken))
                .andReturn());

        mockMvc.perform(multipart("/api/v1/me/labs/{labId}/attachments", 999L)
                        .file(new MockMultipartFile("file", "report.txt", "text/plain", "lab".getBytes()))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonTestSupport.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void assertClientFailureWithoutRateLimit(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isBetween(400, 499).isNotEqualTo(429);
    }
}
