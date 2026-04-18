package com.aubb.server.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class CourseDiscussionIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    notification_receipts,
                    notifications,
                    audit_logs,
                    course_discussion_posts,
                    course_discussions,
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
                    auth_sessions,
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

        insertUser(1L, "school-admin", "School Admin", "school-admin@example.com");
        insertUser(2L, "eng-admin", "Engineering Admin", "eng-admin@example.com");
        insertUser(2L, "teacher-main", "Teacher Main", "teacher-main@example.com");
        insertUser(2L, "student-a", "Student A", "student-a@example.com");
        insertUser(2L, "student-b", "Student B", "student-b@example.com");

        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 2L, "COLLEGE_ADMIN", "eng-admin");
    }

    @Test
    void discussionThreadsAndRepliesAreScopedByTeachingClass() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        String studentBToken = login("student-b", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A 班");
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B 班");
        addStudent(teacherToken, offeringId, 4L, classAId);
        addStudent(teacherToken, offeringId, 5L, classBId);
        studentAToken = login("student-a", "Password123");
        studentBToken = login("student-b", "Password123");

        Long offeringDiscussionId = createTeacherDiscussion(teacherToken, offeringId, null, "课程问答", "统一讨论区");
        Long classDiscussionId = createMyDiscussion(studentAToken, classAId, "A 班实验讨论", "这里讨论本班实验安排");

        createMyReply(studentAToken, offeringDiscussionId, "收到，准备开始");
        createTeacherReply(teacherToken, classDiscussionId, "本周内会补充说明");

        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                        SELECT COUNT(*)
                        FROM notification_receipts nr
                        JOIN notifications n ON n.id = nr.notification_id
                        WHERE n.type = 'COURSE_DISCUSSION_UPDATED'
                        """), 5);

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/discussions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].title", containsInAnyOrder("课程问答", "A 班实验讨论")));

        mockMvc.perform(get("/api/v1/me/course-classes/{teachingClassId}/discussions", classAId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].title", containsInAnyOrder("课程问答", "A 班实验讨论")));

        mockMvc.perform(get("/api/v1/me/course-classes/{teachingClassId}/discussions", classBId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("课程问答"));

        mockMvc.perform(get("/api/v1/me/discussions/{discussionId}", classDiscussionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(classDiscussionId))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[*].body", containsInAnyOrder("这里讨论本班实验安排", "本周内会补充说明")));

        mockMvc.perform(get("/api/v1/me/discussions/{discussionId}", classDiscussionId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void discussionFeatureDisabledAndLockedThreadBlockStudentOperations() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A 班");
        addStudent(teacherToken, offeringId, 4L, classAId);
        studentAToken = login("student-a", "Password123");

        Long offeringDiscussionId = createTeacherDiscussion(teacherToken, offeringId, null, "课程问答", "统一讨论区");

        mockMvc.perform(put("/api/v1/teacher/discussions/{discussionId}/lock-state", offeringDiscussionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {"locked":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        mockMvc.perform(post("/api/v1/me/discussions/{discussionId}/replies", offeringDiscussionId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {"body":"锁定后不应允许学生回复"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/teacher/course-classes/{teachingClassId}/features", classAId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "announcementEnabled":true,
                                  "discussionEnabled":false,
                                  "resourceEnabled":true,
                                  "labEnabled":true,
                                  "assignmentEnabled":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.discussionEnabled").value(false));

        mockMvc.perform(get("/api/v1/me/course-classes/{teachingClassId}/discussions", classAId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/me/discussions/{discussionId}", offeringDiscussionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/me/course-classes/{teachingClassId}/discussions", classAId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"关闭后发帖","body":"这里不应该成功"}
                                """))
                .andExpect(status().isForbidden());
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

    private Long createTeacherDiscussion(String token, Long offeringId, Long teachingClassId, String title, String body)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/discussions", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"teachingClassId":%s,"title":"%s","body":"%s"}
                                """.formatted(teachingClassId == null ? "null" : teachingClassId, title, body)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createMyDiscussion(String token, Long teachingClassId, String title, String body) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/me/course-classes/{teachingClassId}/discussions", teachingClassId)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("""
                                {"title":"%s","body":"%s"}
                                """.formatted(title, body)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private void createTeacherReply(String token, Long discussionId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/discussions/{discussionId}/replies", discussionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"body":"%s"}
                                """.formatted(body)))
                .andExpect(status().isCreated());
    }

    private void createMyReply(String token, Long discussionId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/me/discussions/{discussionId}/replies", discussionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"body":"%s"}
                                """.formatted(body)))
                .andExpect(status().isCreated());
    }

    private void addStudent(String token, Long offeringId, Long userId, Long teachingClassId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"members":[{"userId":%s,"memberRole":"STUDENT","teachingClassId":%s,"remark":"测试学生"}]}
                                """.formatted(userId, teachingClassId)))
                .andExpect(status().isOk());
    }

    private Long createTerm(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/academic-terms")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"termCode":"2026-SPRING","termName":"2026 春季学期","schoolYear":"2025-2026","semester":"SPRING","startDate":"2026-02-20","endDate":"2026-07-10"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createCatalog(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-catalogs")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"courseCode":"CS101","courseName":"数据结构","courseType":"REQUIRED","credit":3.0,"totalHours":48,"departmentUnitId":2,"description":"核心课程"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createOffering(String token, Long catalogId, Long termId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"catalogId":%s,"termId":%s,"offeringCode":"CS101-2026SP-01","offeringName":"数据结构（2026春）","primaryCollegeUnitId":2,"deliveryMode":"HYBRID","language":"ZH","capacity":120,"instructorUserIds":[3],"startAt":"2026-02-20T08:00:00+08:00","endAt":"2026-07-10T23:59:59+08:00"}
                                """.formatted(catalogId, termId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createTeachingClass(String token, Long offeringId, String classCode, String className)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"classCode":"%s","className":"%s","entryYear":2026,"capacity":60,"scheduleSummary":"周二 1-2 节"}
                                """.formatted(classCode, className)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
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

    private long queryForCount(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private Long readLong(MvcResult result, String expression) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), expression);
        return value instanceof Integer integer ? integer.longValue() : Long.parseLong(String.valueOf(value));
    }
}
