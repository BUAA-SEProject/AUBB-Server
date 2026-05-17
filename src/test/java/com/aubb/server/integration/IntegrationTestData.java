package com.aubb.server.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 集成测试共享数据与工具方法。
 * <p>
 * 消除各测试类中重复的 TRUNCATE、INSERT、login、CRUD helper。
 */
public final class IntegrationTestData {

    public static final String DEFAULT_PASSWORD = "Password123";
    public static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private IntegrationTestData() {}

    // ── 常用 TRUNCATE SQL ──────────────────────────────────────────────

    /** 14 表核心集（~17 个测试共用） */
    private static final String CORE_TABLES = """
            audit_logs,
            auth_sessions,
            user_scope_roles,
            platform_configs,
            users,
            org_units,
            academic_profiles,
            user_org_memberships,
            course_catalogs,
            academic_terms,
            course_offerings,
            course_offering_college_maps,
            teaching_classes,
            course_members""";

    /** 角色绑定表 */
    private static final String ROLE_BINDINGS = "role_bindings";

    /** 作业相关表 */
    private static final String ASSIGNMENT_TABLES = """
            assignments,
            assignment_sections,
            assignment_questions,
            assignment_question_options,
            assignment_judge_cases,
            assignment_judge_profiles""";

    /** 提交相关表 */
    private static final String SUBMISSION_TABLES = """
            submissions,
            submission_artifacts,
            submission_answers""";

    /** 判题相关表 */
    private static final String JUDGE_TABLES = "judge_jobs";

    /** 通知相关表 */
    private static final String NOTIFICATION_TABLES = """
            notification_receipts,
            notifications""";

    /**
     * 重置数据库：清空所有常用表并重建基础 org hierarchy。
     * <p>
     * 等价于大多数测试的 @BeforeEach 前半段。测试特有的表在调用后自行 truncate。
     */
    public static void resetDatabase(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE %s, %s, %s, %s, %s, %s RESTART IDENTITY CASCADE"
                .formatted(
                        CORE_TABLES,
                        ROLE_BINDINGS,
                        ASSIGNMENT_TABLES,
                        SUBMISSION_TABLES,
                        JUDGE_TABLES,
                        NOTIFICATION_TABLES));
        insertBaseOrgHierarchy(jdbc);
    }

    /**
     * 重置数据库并插入基础用户和角色绑定。
     * <p>
     * 用于需要完整基础数据集的测试（如 AssignmentIntegrationTests）。
     */
    public static void resetDatabaseWithBaseUsers(JdbcTemplate jdbc) {
        resetDatabase(jdbc);
        insertBaseUsers(jdbc);
        insertBaseRoleBindings(jdbc);
    }

    // ── Org Hierarchy ──────────────────────────────────────────────────

    /** 插入 SCH-1 (SCHOOL) → COL-ENG (COLLEGE) 两级组织。 */
    public static void insertBaseOrgHierarchy(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO org_units (code, name, type, level, sort_order, status)
                VALUES ('SCH-1', 'AUBB School', 'SCHOOL', 1, 1, 'ACTIVE')""");
        jdbc.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (1, 'COL-ENG', 'Engineering', 'COLLEGE', 2, 1, 'ACTIVE')""");
    }

    // ── Users ──────────────────────────────────────────────────────────

    /** 插入 6 个标准用户：school-admin@1, eng-admin@2, teacher-main@2, student-a@2, student-b@2, ta-user@2。 */
    public static void insertBaseUsers(JdbcTemplate jdbc) {
        insertUser(jdbc, 1L, "school-admin", "School Admin", "school-admin@example.com");
        insertUser(jdbc, 2L, "eng-admin", "Engineering Admin", "eng-admin@example.com");
        insertUser(jdbc, 2L, "teacher-main", "Teacher Main", "teacher-main@example.com");
        insertUser(jdbc, 2L, "student-a", "Student A", "student-a@example.com");
        insertUser(jdbc, 2L, "student-b", "Student B", "student-b@example.com");
        insertUser(jdbc, 2L, "ta-user", "TA User", "ta-user@example.com");
    }

    /** 插入单个用户，密码统一为 {@link #DEFAULT_PASSWORD}。 */
    public static void insertUser(
            JdbcTemplate jdbc, Long primaryOrgUnitId, String username, String displayName, String email) {
        jdbc.update(
                """
                INSERT INTO users (
                    primary_org_unit_id, username, display_name, email,
                    password_hash, account_status, failed_login_attempts
                ) VALUES (?, ?, ?, ?, ?, ?, ?)""",
                primaryOrgUnitId,
                username,
                displayName,
                email,
                PASSWORD_ENCODER.encode(DEFAULT_PASSWORD),
                "ACTIVE",
                0);
    }

    // ── Role Bindings ──────────────────────────────────────────────────

    /** 插入标准角色绑定：school-admin → SCHOOL_ADMIN@1, eng-admin → COLLEGE_ADMIN@2。 */
    public static void insertBaseRoleBindings(JdbcTemplate jdbc) {
        insertRoleBinding(jdbc, "school-admin", 1L, "SCHOOL_ADMIN");
        insertRoleBinding(jdbc, "eng-admin", 2L, "COLLEGE_ADMIN");
    }

    /** 为指定用户插入角色绑定。 */
    public static void insertRoleBinding(JdbcTemplate jdbc, String username, Long orgUnitId, String roleCode) {
        jdbc.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?""", orgUnitId, roleCode, username);
    }

    // ── Login / Read Helpers ───────────────────────────────────────────

    /** 登录并返回 accessToken。 */
    public static String login(MockMvc mockMvc, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonTestSupport.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    /** 登录并返回 accessToken（使用默认密码）。 */
    public static String login(MockMvc mockMvc, String username) throws Exception {
        return login(mockMvc, username, DEFAULT_PASSWORD);
    }

    /** 从 MvcResult 中读取 Long 值。 */
    public static Long readLong(MvcResult result, String expression) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), expression);
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        return Long.parseLong(String.valueOf(value));
    }

    /** 执行返回单个整数的 SQL 查询。 */
    public static int queryForCount(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // ── Domain CRUD Helpers ────────────────────────────────────────────

    /** 创建学术学期，返回 ID。 */
    public static Long createTerm(MockMvc mockMvc, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/academic-terms")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "termCode":"2026-SPRING",
                                  "termName":"2026 春季学期",
                                  "schoolYear":"2025-2026",
                                  "semester":"SPRING",
                                  "startDate":"2026-02-20",
                                  "endDate":"2026-07-10"
                                }"""))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    /** 创建课程目录，返回 ID。 */
    public static Long createCatalog(MockMvc mockMvc, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-catalogs")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "courseCode":"CS101",
                                  "courseName":"数据结构",
                                  "courseType":"REQUIRED",
                                  "credit":3.0,
                                  "totalHours":48,
                                  "departmentUnitId":2,
                                  "description":"核心课程"
                                }"""))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    /** 创建开课（默认 code=CS101-2026SP-01, name=数据结构（2026春）），返回 ID。 */
    public static Long createOffering(MockMvc mockMvc, String token, Long catalogId, Long termId) throws Exception {
        return createOffering(mockMvc, token, catalogId, termId, "CS101-2026SP-01", "数据结构（2026春）");
    }

    /** 创建开课（自定义 code/name），返回 ID。 */
    public static Long createOffering(
            MockMvc mockMvc, String token, Long catalogId, Long termId, String offeringCode, String offeringName)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "catalogId":%s,
                                  "termId":%s,
                                  "offeringCode":"%s",
                                  "offeringName":"%s",
                                  "primaryCollegeUnitId":2,
                                  "secondaryCollegeUnitIds":[],
                                  "deliveryMode":"HYBRID",
                                  "language":"ZH",
                                  "capacity":120,
                                  "instructorUserIds":[3],
                                  "startAt":"2026-02-20T08:00:00+08:00",
                                  "endAt":"2026-07-10T23:59:59+08:00"
                                }""".formatted(catalogId, termId, offeringCode, offeringName)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    /** 创建教学班（默认 entryYear=2026），返回 ID。 */
    public static Long createTeachingClass(
            MockMvc mockMvc, String token, Long offeringId, String classCode, String className) throws Exception {
        return createTeachingClass(mockMvc, token, offeringId, classCode, className, 2026);
    }

    /** 创建教学班，返回 ID。 */
    public static Long createTeachingClass(
            MockMvc mockMvc, String token, Long offeringId, String classCode, String className, int entryYear)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "classCode":"%s",
                                          "className":"%s",
                                          "entryYear":%s,
                                          "capacity":60,
                                          "scheduleSummary":"周二 1-2 节"
                                        }""".formatted(classCode, className, entryYear)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    /** 批量添加课程成员（默认角色 STUDENT）。 */
    public static void addMember(MockMvc mockMvc, String token, Long offeringId, Long userId, Long classId)
            throws Exception {
        addMember(mockMvc, token, offeringId, userId, "STUDENT", classId);
    }

    /** 批量添加课程成员。 */
    public static void addMember(
            MockMvc mockMvc, String token, Long offeringId, Long userId, String roleCode, Long classId)
            throws Exception {
        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":%s,"memberRole":"%s","teachingClassId":%s,"remark":"seed"}
                                  ]
                                }""".formatted(userId, roleCode, classId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));
    }

    /** 创建作业（无指定教学班），返回 ID。 */
    public static Long createAssignment(MockMvc mockMvc, String token, Long offeringId, String title) throws Exception {
        return createAssignment(mockMvc, token, offeringId, null, title);
    }

    /** 创建作业（指定时间窗口），返回 ID。 */
    public static Long createAssignment(
            MockMvc mockMvc,
            String token,
            Long offeringId,
            Long teachingClassId,
            String title,
            OffsetDateTime openAt,
            OffsetDateTime dueAt)
            throws Exception {
        return createAssignment(mockMvc, token, offeringId, teachingClassId, title, openAt, dueAt, 3);
    }

    /** 创建作业（指定时间窗口和提交次数），返回 ID。 */
    public static Long createAssignment(
            MockMvc mockMvc,
            String token,
            Long offeringId,
            Long teachingClassId,
            String title,
            OffsetDateTime openAt,
            OffsetDateTime dueAt,
            int maxSubmissions)
            throws Exception {
        String classField = teachingClassId == null ? "null" : String.valueOf(teachingClassId);
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "title":"%s",
                                          "description":"任务说明",
                                          "teachingClassId":%s,
                                          "openAt":"%s",
                                          "dueAt":"%s",
                                          "maxSubmissions":%d
                                        }""".formatted(title, classField, openAt.toString(), dueAt.toString(), maxSubmissions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
    }

    /** 创建作业，返回 ID。 */
    public static Long createAssignment(
            MockMvc mockMvc, String token, Long offeringId, Long teachingClassId, String title) throws Exception {
        String classField = teachingClassId == null ? "null" : String.valueOf(teachingClassId);
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "title":"%s",
                                          "description":"任务说明",
                                          "teachingClassId":%s,
                                          "openAt":"2026-03-01T08:00:00+08:00",
                                          "dueAt":"2026-03-08T23:59:59+08:00",
                                          "maxSubmissions":3
                                        }""".formatted(title, classField)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
    }

    /** 发布作业。 */
    public static void publishAssignment(MockMvc mockMvc, String token, Long assignmentId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/publish", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
}
