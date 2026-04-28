package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.common.storage.ObjectStorageService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class LabReportIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-lab-test-assets";

    @Container
    static final GenericContainer<?> MINIO_CONTAINER = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectStorageService objectStorageService;

    private String latestTeacherToken;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("aubb.storage.minio.enabled", () -> "true");
        registry.add("aubb.storage.minio.auto-create-bucket", () -> "true");
        registry.add(
                "aubb.storage.minio.endpoint",
                () -> "http://" + MINIO_CONTAINER.getHost() + ":" + MINIO_CONTAINER.getMappedPort(9000));
        registry.add("aubb.storage.minio.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("aubb.storage.minio.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("aubb.storage.minio.bucket", () -> MINIO_BUCKET);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    lab_report_attachments,
                    lab_reports,
                    labs,
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
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (1, 'COL-ENG', 'Engineering', 'COLLEGE', 2, 1, 'ACTIVE')
                """);

        insertUser(1L, "school-admin", "School Admin", "school-admin@example.com");
        insertUser(2L, "eng-admin", "Engineering Admin", "eng-admin@example.com");
        insertUser(2L, "teacher-main", "Teacher Main", "teacher-main@example.com");
        insertUser(2L, "student-a", "Student A", "student-a@example.com");
        insertUser(2L, "ta-user", "Ta User", "ta-user@example.com");

        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 2L, "COLLEGE_ADMIN", "eng-admin");
        latestTeacherToken = null;
    }

    @Test
    void teacherAndStudentCompleteLabReportFlowWithObjectStorageReplay() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-LAB", "实验班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long labId = createLab(teacherToken, offeringId, classId, "操作系统实验", "完成进程调度报告");
        publishLab(teacherToken, labId);

        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 4
                                  AND n.type = 'LAB_PUBLISHED'
                                """), 1);

        mockMvc.perform(get("/api/v1/me/course-classes/{teachingClassId}/labs", classId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(labId))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"));

        Long attachmentId =
                uploadAttachment(studentToken, labId, "report.pdf", "application/pdf", "%PDF-1.7\nlab-report");
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM lab_report_attachments WHERE id = ?", String.class, attachmentId);
        assertThat(objectStorageService.getObject(objectKey).content())
                .isEqualTo("%PDF-1.7\nlab-report".getBytes(StandardCharsets.UTF_8));

        Long reportId = saveReport(studentToken, labId, "第一次实验报告正文", attachmentId, true);

        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 3
                                  AND n.type = 'LAB_REPORT_SUBMITTED'
                                """), 1);

        mockMvc.perform(get("/api/v1/teacher/labs/{labId}/reports", labId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(reportId))
                .andExpect(jsonPath("$.items[0].student.displayName").value("Student A"))
                .andExpect(jsonPath("$.items[0].attachmentCount").value(1))
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/teacher/lab-reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].id").value(attachmentId))
                .andExpect(jsonPath("$.teacherCommentText").value(nullValue()));

        reviewReport(teacherToken, reportId, "请补充实验环境截图", "整体结构清晰，但证据不足");

        mockMvc.perform(get("/api/v1/me/labs/{labId}/report", labId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.teacherAnnotationText").value(nullValue()))
                .andExpect(jsonPath("$.teacherCommentText").value(nullValue()));

        publishReview(teacherToken, reportId);

        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 4
                                  AND n.type = 'LAB_REPORT_PUBLISHED'
                                """), 1);

        mockMvc.perform(get("/api/v1/me/labs/{labId}/report", labId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.teacherAnnotationText").value("请补充实验环境截图"))
                .andExpect(jsonPath("$.teacherCommentText").value("整体结构清晰，但证据不足"))
                .andExpect(jsonPath("$.attachments[0].originalFilename").value("report.pdf"));

        mockMvc.perform(get("/api/v1/me/lab-report-attachments/{attachmentId}/download", attachmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("report.pdf")))
                .andExpect(content().bytes("%PDF-1.7\nlab-report".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/teacher/lab-report-attachments/{attachmentId}/download", attachmentId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(content().bytes("%PDF-1.7\nlab-report".getBytes(StandardCharsets.UTF_8)));

        assertThat(queryForCount("SELECT COUNT(*) FROM labs")).isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM lab_reports")).isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM lab_report_attachments")).isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'LAB_REPORT_PUBLISHED'"))
                .isEqualTo(1);
    }

    @Test
    void teacherCanUpdateAndCloseLabAndStudentCanNoLongerSubmit() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-LAB2", "实验二班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long labId = createLab(teacherToken, offeringId, classId, "数据库实验", "初版说明");
        updateLab(teacherToken, labId, "数据库实验-更新", "更新后的实验说明");
        publishLab(teacherToken, labId);
        closeLab(teacherToken, labId);

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/labs", offeringId)
                        .param("status", "CLOSED")
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("数据库实验-更新"))
                .andExpect(jsonPath("$.items[0].status").value("CLOSED"));

        mockMvc.perform(put("/api/v1/me/labs/{labId}/report", labId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportContentText":"关闭后尝试提交",
                                  "submit":true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB_STATUS_INVALID"));
    }

    @Test
    void studentCannotUploadMoreThanTenPendingLabAttachments() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-LAB-LIMIT", "实验限制班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long labId = createLab(teacherToken, offeringId, classId, "附件上限实验", "验证实验附件上传上限");
        publishLab(teacherToken, labId);

        for (int index = 1; index <= 10; index++) {
            uploadAttachment(
                    studentToken,
                    labId,
                    "attachment-%02d.txt".formatted(index),
                    "text/plain",
                    "attachment-%02d".formatted(index));
        }

        mockMvc.perform(multipart("/api/v1/me/labs/{labId}/attachments", labId)
                        .file(new MockMultipartFile(
                                "file",
                                "attachment-11.txt",
                                "text/plain",
                                "attachment-11".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB_REPORT_ATTACHMENT_LIMIT"));

        assertThat(queryForCount("SELECT COUNT(*) FROM lab_report_attachments WHERE lab_id = " + labId))
                .isEqualTo(10);
    }

    @Test
    void labFeatureDisabledBlocksTeacherAndStudentEndpoints() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-LAB3", "实验三班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long labId = createLab(teacherToken, offeringId, classId, "网络实验", "先创建再关闭能力");
        publishLab(teacherToken, labId);
        updateTeachingClassFeatures(teacherToken, classId, false);

        mockMvc.perform(get("/api/v1/teacher/labs/{labId}", labId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB_DISABLED"));

        mockMvc.perform(get("/api/v1/me/course-classes/{teachingClassId}/labs", classId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB_DISABLED"));

        mockMvc.perform(multipart("/api/v1/me/labs/{labId}/attachments", labId)
                        .file(new MockMultipartFile(
                                "file", "blocked.txt", "text/plain", "blocked".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB_DISABLED"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private Long createTerm(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/academic-terms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "termCode":"2026-SPRING",
                                  "termName":"2026 春季学期",
                                  "schoolYear":"2025-2026",
                                  "semester":"SPRING",
                                  "startDate":"2026-02-20",
                                  "endDate":"2026-07-10"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private Long createCatalog(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-catalogs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseCode":"CS301",
                                  "courseName":"系统实验课程",
                                  "courseType":"REQUIRED",
                                  "credit":3.0,
                                  "totalHours":48,
                                  "departmentUnitId":2,
                                  "description":"实验教学课程"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private Long createOffering(String token, Long catalogId, Long termId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "catalogId":%s,
                                  "termId":%s,
                                  "offeringCode":"CS301-2026SP-01",
                                  "offeringName":"系统实验课程-2026春",
                                  "primaryCollegeUnitId":2,
                                  "deliveryMode":"HYBRID",
                                  "language":"ZH",
                                  "capacity":120,
                                  "instructorUserIds":[3],
                                  "startAt":"2026-02-20T08:00:00+08:00",
                                  "endAt":"2026-07-10T23:59:59+08:00"
                                }
                                """.formatted(catalogId, termId)))
                .andExpect(status().isCreated())
                .andReturn();
        latestTeacherToken = login("teacher-main", "Password123");
        return readId(result);
    }

    private Long createTeachingClass(String token, Long offeringId, String classCode, String className, int entryYear)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classCode":"%s",
                                  "className":"%s",
                                  "entryYear":%s,
                                  "capacity":40,
                                  "scheduleSummary":"周三下午"
                                }
                                """.formatted(classCode, className, entryYear)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private void addMember(String token, Long offeringId, Long userId, String roleCode, Long classId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "members":[
                                    {
                                      "userId":%s,
                                      "memberRole":"%s",
                                      "teachingClassId":%s
                                    }
                                  ]
                                }
                                """.formatted(userId, roleCode, classId)))
                .andExpect(status().isOk());
    }

    private void updateTeachingClassFeatures(String token, Long teachingClassId, boolean labEnabled) throws Exception {
        mockMvc.perform(put("/api/v1/teacher/course-classes/{teachingClassId}/features", teachingClassId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "announcementEnabled":true,
                                  "discussionEnabled":true,
                                  "resourceEnabled":true,
                                  "labEnabled":%s,
                                  "assignmentEnabled":true
                                }
                                """.formatted(labEnabled)))
                .andExpect(status().isOk());
    }

    private Long createLab(String token, Long offeringId, Long teachingClassId, String title, String description)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/labs", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teachingClassId":%s,
                                  "title":"%s",
                                  "description":"%s"
                                }
                                """.formatted(teachingClassId, title, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private void updateLab(String token, Long labId, String title, String description) throws Exception {
        mockMvc.perform(put("/api/v1/teacher/labs/{labId}", labId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"%s",
                                  "description":"%s"
                                }
                                """.formatted(title, description)))
                .andExpect(status().isOk());
    }

    private void publishLab(String token, Long labId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/labs/{labId}/publish", labId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token)))
                .andExpect(status().isOk());
    }

    private void closeLab(String token, Long labId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/labs/{labId}/close", labId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token)))
                .andExpect(status().isOk());
    }

    private Long uploadAttachment(String token, Long labId, String filename, String contentType, String content)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/me/labs/{labId}/attachments", labId)
                        .file(new MockMultipartFile(
                                "file", filename, contentType, content.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private Long saveReport(String token, Long labId, String reportContentText, Long attachmentId, boolean submit)
            throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/me/labs/{labId}/report", labId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportContentText":"%s",
                                  "attachmentIds":[%s],
                                  "submit":%s
                                }
                                """.formatted(reportContentText, attachmentId, submit)))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private void reviewReport(String token, Long reportId, String annotation, String comment) throws Exception {
        mockMvc.perform(put("/api/v1/teacher/lab-reports/{reportId}/review", reportId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teacherAnnotationText":"%s",
                                  "teacherCommentText":"%s"
                                }
                                """.formatted(annotation, comment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));
    }

    private void publishReview(String token, Long reportId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/lab-reports/{reportId}/publish", reportId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private String resolveTeacherToken(String token) {
        return latestTeacherToken == null ? token : latestTeacherToken;
    }

    private void insertUser(Long primaryOrgUnitId, String username, String displayName, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO users (primary_org_unit_id, username, display_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, primaryOrgUnitId, username, displayName, email, PASSWORD_ENCODER.encode("Password123"));
    }

    private long queryForCount(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private Long readId(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }
}
