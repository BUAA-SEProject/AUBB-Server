package com.aubb.server.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

class NotificationCenterIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisIntegrationTestSupport.registerRedisProperties(registry);
        registry.add("aubb.redis.rate-limit.enabled", () -> "false");
        registry.add("aubb.redis.cache.notification-unread-ttl", () -> "PT5M");
    }

    @BeforeEach
    void setUp() {
        RedisIntegrationTestSupport.flushAll();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    notification_receipts,
                    notifications,
                    auth_sessions,
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
                VALUES (1, 'COL-ENG', 'Engineering', 'COLLEGE', 2, 1, 'ACTIVE')
                """);

        insertUser(2L, "student-a", "Student A", "student-a@example.com");
        insertUser(2L, "teacher-main", "Teacher Main", "teacher-main@example.com");
    }

    @Test
    void myNotificationsSupportUnreadCountMarkReadAndReadAll() throws Exception {
        String studentToken = login("student-a", "Password123");

        Long assignmentNotificationId = insertNotification(
                "ASSIGNMENT_PUBLISHED",
                "新作业已发布：链表实验",
                "请在截止时间前完成提交。",
                2L,
                "ASSIGNMENT",
                "11",
                "2026-04-17T10:00:00+08:00");
        Long gradeNotificationId = insertNotification(
                "ASSIGNMENT_GRADES_PUBLISHED",
                "成绩已发布：链表实验",
                "该作业的成绩与反馈已发布，现在可以查看。",
                2L,
                "ASSIGNMENT",
                "11",
                "2026-04-17T10:05:00+08:00");
        Long appealNotificationId = insertNotification(
                "GRADE_APPEAL_RESOLVED",
                "成绩申诉已处理：链表实验",
                "你的成绩申诉已通过，最新成绩已更新。",
                2L,
                "GRADE_APPEAL",
                "7",
                "2026-04-17T10:10:00+08:00");

        insertReceipt(assignmentNotificationId, 1L, null, "2026-04-17T10:00:00+08:00");
        insertReceipt(gradeNotificationId, 1L, null, "2026-04-17T10:05:00+08:00");
        insertReceipt(appealNotificationId, 1L, "2026-04-17T10:15:00+08:00", "2026-04-17T10:10:00+08:00");

        Long teacherOnlyNotificationId = insertNotification(
                "LAB_REPORT_SUBMITTED",
                "实验报告待评阅：网络实验",
                "有学生提交了新的实验报告，请及时评阅。",
                1L,
                "LAB_REPORT",
                "21",
                "2026-04-17T10:20:00+08:00");
        insertReceipt(teacherOnlyNotificationId, 2L, null, "2026-04-17T10:20:00+08:00");

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(get("/api/v1/me/notifications")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").value(appealNotificationId))
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.items[1].id").value(gradeNotificationId))
                .andExpect(jsonPath("$.items[1].read").value(false))
                .andExpect(jsonPath("$.items[2].id").value(assignmentNotificationId))
                .andExpect(jsonPath("$.items[2].read").value(false));

        mockMvc.perform(post("/api/v1/me/notifications/{notificationId}/read", gradeNotificationId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(gradeNotificationId))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post("/api/v1/me/notifications/read-all").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void userCannotReadAnotherUsersNotification() throws Exception {
        String studentToken = login("student-a", "Password123");
        Long teacherOnlyNotificationId = insertNotification(
                "LAB_REPORT_SUBMITTED",
                "实验报告待评阅：网络实验",
                "有学生提交了新的实验报告，请及时评阅。",
                1L,
                "LAB_REPORT",
                "21",
                "2026-04-17T10:20:00+08:00");
        insertReceipt(teacherOnlyNotificationId, 2L, null, "2026-04-17T10:20:00+08:00");

        mockMvc.perform(post("/api/v1/me/notifications/{notificationId}/read", teacherOnlyNotificationId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void myNotificationsExposeOptionalSseStreamEndpoint() throws Exception {
        String studentToken = login("student-a", "Password123");

        mockMvc.perform(get("/api/v1/me/notifications/stream").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    void unreadCountCachesHitsAndEvictsAfterReadMutations() throws Exception {
        String studentToken = login("student-a", "Password123");

        Long assignmentNotificationId = insertNotification(
                "ASSIGNMENT_PUBLISHED",
                "新作业已发布：链表实验",
                "请在截止时间前完成提交。",
                2L,
                "ASSIGNMENT",
                "11",
                "2026-04-17T10:00:00+08:00");
        insertReceipt(assignmentNotificationId, 1L, null, "2026-04-17T10:00:00+08:00");

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        Long laterNotificationId = insertNotification(
                "LAB_PUBLISHED", "新实验已发布：网络实验", "实验已发布，请完成报告。", 2L, "LAB", "21", "2026-04-17T10:10:00+08:00");
        insertReceipt(laterNotificationId, 1L, null, "2026-04-17T10:10:00+08:00");

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post("/api/v1/me/notifications/read-all").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "aubb_cache_operations_total{cache=\"notificationUnreadCount\",operation=\"get\",result=\"hit\"}")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "aubb_cache_operations_total{cache=\"notificationUnreadCount\",operation=\"evict\",result=\"success\"}")));
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

    private String login(String username, String password) throws Exception {
        return JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content("""
                                        {
                                          "username":"%s",
                                          "password":"%s"
                                        }
                                        """.formatted(username, password)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.accessToken");
    }

    private Long insertNotification(
            String type,
            String title,
            String body,
            Long actorUserId,
            String targetType,
            String targetId,
            String createdAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO notifications (
                    type,
                    title,
                    body,
                    actor_user_id,
                    target_type,
                    target_id,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::timestamptz)
                RETURNING id
                """, Long.class, type, title, body, actorUserId, targetType, targetId, createdAt);
    }

    private void insertReceipt(Long notificationId, Long recipientUserId, String readAt, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_receipts (
                    notification_id,
                    recipient_user_id,
                    read_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, notificationId, recipientUserId, readAt, createdAt, readAt == null ? createdAt : readAt);
    }
}
