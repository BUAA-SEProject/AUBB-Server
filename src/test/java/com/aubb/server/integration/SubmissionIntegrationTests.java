package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.submission.application.SubmissionApplicationService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SubmissionIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final DateTimeFormatter OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-submission-test-assets";

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
    private SubmissionApplicationService submissionApplicationService;

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
        RedisIntegrationTestSupport.flushAll();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    auth_sessions,
                    role_bindings,
                    auth_group_members,
                    auth_groups,
                    judge_jobs,
                    submission_artifacts,
                    submissions,
                    assignments,
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
        insertUser(2L, "student-b", "Student B", "student-b@example.com");
        insertUser(2L, "ta-user", "Ta User", "ta-user@example.com");
        latestTeacherToken = null;

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
    void studentCreatesSubmissionAndTeacherCanReviewSubmissions() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "栈与队列作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "第一次正式提交");

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(submissionId))
                .andExpect(jsonPath("$.items[0].attemptNo").value(1))
                .andExpect(jsonPath("$.items[0].contentText").value("第一次正式提交"));

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].submitterUserId").value(4))
                .andExpect(jsonPath("$.items[0].attemptNo").value(1));

        assertThat(queryForCount("SELECT COUNT(*) FROM submissions WHERE assignment_id = 1"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'SUBMISSION_CREATED'"))
                .isEqualTo(1);
    }

    @Test
    void classScopedStudentCanReadOwnSubmissionHistoryForOfferingWideAssignment() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-OFFERING", "公共任务班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                null,
                "开课公共提交读取作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "公共任务首次提交");

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("开课公共提交读取作业"));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(submissionId))
                .andExpect(jsonPath("$.items[0].contentText").value("公共任务首次提交"));

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId))
                .andExpect(jsonPath("$.contentText").value("公共任务首次提交"));
    }

    @Test
    void studentCannotAccessTeacherSubmissionReadRoutes() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        String studentBToken = login("student-b", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 5L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");
        studentBToken = login("student-b", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "教师读路径隔离作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentBToken, assignmentId, "同班同学提交");

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void studentUploadsArtifactsCreatesSubmissionAndTeacherDownloadsArtifact() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "附件提交作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long artifactId = uploadArtifact(studentAToken, assignmentId, "answer.py", "text/x-python", "print('AUBB')\n");
        Long submissionId = createSubmission(studentAToken, assignmentId, "代码见附件", artifactId);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(1))
                .andExpect(jsonPath("$.artifacts[0].id").value(artifactId))
                .andExpect(jsonPath("$.artifacts[0].originalFilename").value("answer.py"))
                .andExpect(jsonPath("$.artifacts[0].sizeBytes").value(14));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(1))
                .andExpect(jsonPath("$.artifacts[0].originalFilename").value("answer.py"));

        mockMvc.perform(get("/api/v1/teacher/submission-artifacts/{artifactId}/download", artifactId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("answer.py")))
                .andExpect(content().bytes("print('AUBB')\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(queryForCount("SELECT COUNT(*) FROM submission_artifacts WHERE assignment_id = 1"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'SUBMISSION_ARTIFACT_UPLOADED'"))
                .isEqualTo(1);
    }

    @Test
    void offeringSubmissionReaderWithoutSensitiveGrantCannotDownloadTeacherArtifact() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        insertUser(2L, "submission-reader", "Submission Reader", "submission-reader@example.com");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "附件敏感权限作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long artifactId =
                uploadArtifact(studentAToken, assignmentId, "secret.py", "text/x-python", "print('secret')\n");
        createSubmission(studentAToken, assignmentId, "包含源码附件", artifactId);

        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
        String templateCode = "submission-reader-" + uniqueSuffix;
        String groupDisplayName = "submission-reader-offering-" + uniqueSuffix;
        Long templateId = insertAuthzGroupTemplate(templateCode, "仅看提交元数据", "OFFERING");
        jdbcTemplate.update(
                "INSERT INTO auth_group_template_permissions (template_id, permission_code) VALUES (?, ?)",
                templateId,
                "submission.read.offering");
        Long groupId = insertAuthzGroup(templateId, groupDisplayName, "OFFERING", offeringId);
        jdbcTemplate.update(
                "INSERT INTO auth_group_members (group_id, user_id, source_type, joined_at) VALUES (?, ?, ?, now())",
                groupId,
                7L,
                "MANUAL");

        String submissionReaderToken = login("submission-reader", "Password123");

        mockMvc.perform(get("/api/v1/teacher/submission-artifacts/{artifactId}/download", artifactId)
                        .header("Authorization", "Bearer " + submissionReaderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void sensitiveSubmissionDownloadShouldWriteAuditWhenExplicitSourceGrantIsUsed() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        insertUser(2L, "source-reader", "Source Reader", "source-reader@example.com");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-SRC", "源码班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "源码访问作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long artifactId = uploadArtifact(studentAToken, assignmentId, "main.py", "text/x-python", "print('source')\n");
        createSubmission(studentAToken, assignmentId, "源码见附件", artifactId);

        String roleCode =
                "submission_source_reader_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO roles (code, name, description, role_category, scope_type, is_builtin, status)
                VALUES (?, ?, ?, 'SPECIAL', 'offering', FALSE, 'ACTIVE')
                """, roleCode, "源码读取者", "显式授权读取源码全文");
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT ?, id
                FROM permissions
                WHERE code = 'submission.read_source'
                """, roleId);
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
                ) VALUES (?, ?, 'offering', ?, '{}'::jsonb, 'ACTIVE', now(), NULL, NULL, 'MANUAL', 0)
                """, 7L, roleId, offeringId);

        String sourceReaderToken = login("source-reader", "Password123");

        mockMvc.perform(get("/api/v1/teacher/submission-artifacts/{artifactId}/download", artifactId)
                        .header("Authorization", "Bearer " + sourceReaderToken))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'SUBMISSION_SOURCE_READ' AND decision = 'ALLOW'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'permissionCode' FROM audit_logs WHERE action = 'SUBMISSION_SOURCE_READ' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("submission.read_source");
    }

    @Test
    void manualSubmissionReadBindingCanReadTeacherSubmissionButContentIsMasked() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        insertUser(2L, "submission-reader", "Submission Reader", "submission-reader@example.com");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "只读提交列表作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "需要被裁剪的源码正文");

        String roleCode = "submission_read_only_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO roles (code, name, description, role_category, scope_type, is_builtin, status)
                VALUES (?, ?, ?, 'TEACHING', 'offering', FALSE, 'ACTIVE')
                """, roleCode, "只读提交审阅者", "仅允许查看提交元数据");
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT ?, id
                FROM permissions
                WHERE code = 'submission.read'
                """, roleId);
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
                ) VALUES (?, ?, 'offering', ?, '{}'::jsonb, 'ACTIVE', now(), NULL, NULL, 'MANUAL', 0)
                """, 7L, roleId, offeringId);

        String submissionReaderToken = login("submission-reader", "Password123");

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + submissionReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].submitterUserId").value(4))
                .andExpect(jsonPath("$.items[0].contentText").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + submissionReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId))
                .andExpect(jsonPath("$.contentText").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void activeStudentWithoutRoleBindingsCannotReadOwnSubmissionHistory() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-HIS", "历史班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "当前学生提交历史",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "当前学生提交正文");

        jdbcTemplate.update("DELETE FROM role_bindings WHERE user_id = ?", 4L);

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void transferredStudentCanReadOwnHistoryButCannotSubmitNewAttempt() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-HIS", "历史班", 2024);
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-NEW", "新班", 2025);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "转班历史读取作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "转班前首次提交");

        Long memberId = findMemberId(offeringId, 4L, "STUDENT");
        mockMvc.perform(post(
                                "/api/v1/teacher/course-offerings/{offeringId}/members/{memberId}/transfer",
                                offeringId,
                                memberId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken))
                        .contentType("application/json")
                        .content("""
                                {"targetTeachingClassId":%s,"remark":"转班到新班"}
                                """.formatted(classBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingClassId").value(classBId))
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isUnauthorized());

        String refreshedStudentToken = login("student-a", "Password123");

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + refreshedStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId));

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + refreshedStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId))
                .andExpect(jsonPath("$.contentText").value("转班前首次提交"));

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + refreshedStudentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"转班后不应再允许正式提交",
                                  "artifactIds":[]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 4"
                        + " AND member_role = 'STUDENT'"))
                .isEqualTo(2);
        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 4"
                        + " AND teaching_class_id = "
                        + classAId
                        + " AND member_status = 'TRANSFERRED'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 4"
                        + " AND teaching_class_id = "
                        + classBId
                        + " AND member_status = 'ACTIVE'"))
                .isEqualTo(1);
    }

    @Test
    void droppedStudentCanReadOwnHistoryButCannotSubmitNewAttempt() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-DROP", "退课班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "退课历史读取作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "退课前首次提交");

        Long memberId = findMemberId(offeringId, 4L, "STUDENT");
        mockMvc.perform(patch(
                                "/api/v1/teacher/course-offerings/{offeringId}/members/{memberId}/status",
                                offeringId,
                                memberId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken))
                        .contentType("application/json")
                        .content("""
                                {"memberStatus":"DROPPED","remark":"退课处理"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberStatus").value("DROPPED"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isUnauthorized());

        String refreshedStudentToken = login("student-a", "Password123");

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + refreshedStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId));

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + refreshedStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId));

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + refreshedStudentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"退课后不得继续提交",
                                  "artifactIds":[]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 4"
                        + " AND teaching_class_id = "
                        + classAId
                        + " AND member_status = 'DROPPED'"))
                .isEqualTo(1);
    }

    @Test
    void studentCannotUploadMoreThanTenPendingArtifactsForSameAssignment() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "附件上限校验作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        for (int index = 1; index <= 10; index++) {
            uploadArtifact(
                    studentAToken,
                    assignmentId,
                    "artifact-%02d.txt".formatted(index),
                    "text/plain",
                    "artifact-%02d".formatted(index));
        }

        mockMvc.perform(multipart("/api/v1/me/assignments/{assignmentId}/submission-artifacts", assignmentId)
                        .file(new MockMultipartFile(
                                "file",
                                "artifact-11.txt",
                                "text/plain",
                                "artifact-11".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_ARTIFACT_LIMIT_EXCEEDED"));

        assertThat(queryForCount("SELECT COUNT(*) FROM submission_artifacts WHERE assignment_id = " + assignmentId))
                .isEqualTo(10);
    }

    @Test
    void studentCannotReuseArtifactAcrossSubmissions() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "附件复用校验作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                3);
        publishAssignment(teacherToken, assignmentId);

        Long artifactId = uploadArtifact(studentAToken, assignmentId, "report.txt", "text/plain", "第一次版本");
        createSubmission(studentAToken, assignmentId, "第一次提交", artifactId);

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"第二次提交",
                                  "artifactIds":[%s]
                                }
                                """.formatted(artifactId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_ARTIFACT_ALREADY_ATTACHED"));
    }

    @Test
    void plainSubmissionDoesNotCreateJudgeJobsWithoutJudgeConfig() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "普通文本作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "这次提交不参与自动评测");
        teacherToken = login("teacher-main", "Password123");

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue", submissionId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_JUDGE_NOT_CONFIGURED"));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(queryForCount("SELECT COUNT(*) FROM judge_jobs WHERE submission_id = 1"))
                .isEqualTo(0);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'JUDGE_JOB_ENQUEUED'"))
                .isEqualTo(0);
    }

    @Test
    void studentCannotSubmitBeyondAssignmentLimit() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "最大次数作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                1);
        publishAssignment(teacherToken, assignmentId);

        createSubmission(studentAToken, assignmentId, "第一次提交");

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"第二次提交"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_LIMIT_REACHED"));
    }

    @Test
    void studentCannotSubmitOutsideAssignmentWindow() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        studentAToken = login("student-a", "Password123");

        Long futureAssignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "未开放作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                2);
        publishAssignment(teacherToken, futureAssignmentId);

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", futureAssignmentId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"过早提交"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_WINDOW_INVALID"));

        Long closedAssignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "已截止作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(3),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusHours(1),
                2);
        publishAssignment(teacherToken, closedAssignmentId);

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", closedAssignmentId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"过晚提交"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_WINDOW_INVALID"));
    }

    @Test
    void studentCannotReadOthersSubmissionAndTeacherDetailWorks() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        String studentBToken = login("student-b", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B班", 2025);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 5L, "STUDENT", classBId);
        studentAToken = login("student-a", "Password123");
        studentBToken = login("student-b", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                null,
                "课程公共作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                3);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "学生A提交");

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitterUserId").value(4))
                .andExpect(jsonPath("$.contentText").value("学生A提交"));
    }

    @Test
    void teacherCanFilterSubmissionsBySubmitterAndLatestOnly() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");
        String studentBToken = login("student-b", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B班", 2025);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 5L, "STUDENT", classBId);
        studentAToken = login("student-a", "Password123");
        studentBToken = login("student-b", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                null,
                "筛选提交作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                5);
        publishAssignment(teacherToken, assignmentId);

        createSubmission(studentAToken, assignmentId, "学生A第一次");
        createSubmission(studentAToken, assignmentId, "学生A第二次");
        createSubmission(studentBToken, assignmentId, "学生B第一次");

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .param("submitterUserId", "4")
                        .param("latestOnly", "true")
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].submitterUserId").value(4))
                .andExpect(jsonPath("$.items[0].attemptNo").value(2))
                .andExpect(jsonPath("$.items[0].contentText").value("学生A第二次"));
    }

    @Test
    void classScopedTaListsHistoricalOfferingSubmissionBySubmissionClassInsteadOfCurrentMembership() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B班", 2025);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 6L, "TA", classAId);
        addMember(teacherToken, offeringId, 5L, "TA", classBId);
        studentAToken = login("student-a", "Password123");
        String classATaToken = login("ta-user", "Password123");
        String classBTaToken = login("student-b", "Password123");

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                null,
                "公共作业历史班级边界",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                3);
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentAToken, assignmentId, "转班前公共作业提交");

        Long memberId = findMemberId(offeringId, 4L, "STUDENT");
        mockMvc.perform(post(
                                "/api/v1/teacher/course-offerings/{offeringId}/members/{memberId}/transfer",
                                offeringId,
                                memberId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken))
                        .contentType("application/json")
                        .content("""
                                {"targetTeachingClassId":%s,"remark":"转班到B班"}
                                """.formatted(classBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingClassId").value(classBId))
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + classATaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(submissionId));

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + classATaToken)
                        .param("latestOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(submissionId));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + classATaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId));

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + classBTaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + classBTaToken)
                        .param("latestOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + classBTaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void concurrentSubmissionsForSameUserAllocateUniqueAttempts() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classAId);

        Long assignmentId = createAssignment(
                teacherToken,
                offeringId,
                classAId,
                "并发提交作业",
                OffsetDateTime.now(ZoneOffset.ofHours(8)).minusDays(1),
                OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(3),
                64);
        publishAssignment(teacherToken, assignmentId);

        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(4L, "student-a", "Student A", 2L, AccountStatus.ACTIVE, null, List.of());
        int concurrentRequests = 24;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch readyLatch = new CountDownLatch(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>(concurrentRequests);
        try {
            for (int index = 0; index < concurrentRequests; index++) {
                final int submissionIndex = index;
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    if (!startLatch.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发提交流水未在预期时间内开始");
                    }
                    return submissionApplicationService
                            .createSubmission(
                                    assignmentId, "并发提交-%d".formatted(submissionIndex), List.of(), null, principal)
                            .attemptNo();
                }));
            }
            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<Integer> attempts = new ArrayList<>(concurrentRequests);
            for (Future<Integer> future : futures) {
                attempts.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(attempts).hasSize(concurrentRequests);
            assertThat(attempts).doesNotHaveDuplicates();
            assertThat(attempts)
                    .containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.rangeClosed(1, concurrentRequests)
                            .boxed()
                            .toList());
            assertThat(queryForCount("SELECT COUNT(*) FROM submissions WHERE assignment_id = " + assignmentId))
                    .isEqualTo(concurrentRequests);
        } finally {
            executorService.shutdownNow();
        }
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

    private Long createTerm(String token) throws Exception {
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
                                }
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
                                {
                                  "courseCode":"CS101",
                                  "courseName":"数据结构",
                                  "courseType":"REQUIRED",
                                  "credit":3.0,
                                  "totalHours":48,
                                  "departmentUnitId":2,
                                  "description":"核心课程"
                                }
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
                                {
                                  "catalogId":%s,
                                  "termId":%s,
                                  "offeringCode":"CS101-2026SP-01",
                                  "offeringName":"数据结构（2026春）",
                                  "primaryCollegeUnitId":2,
                                  "secondaryCollegeUnitIds":[],
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
        return readLong(result, "$.id");
    }

    private Long createTeachingClass(String token, Long offeringId, String classCode, String className, int entryYear)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType("application/json")
                        .content("""
                                {
                                  "classCode":"%s",
                                  "className":"%s",
                                  "entryYear":%s,
                                  "capacity":60,
                                  "scheduleSummary":"周二 1-2 节"
                                }
                                """.formatted(classCode, className, entryYear)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long insertAuthzGroupTemplate(String code, String displayName, String scopeType) {
        jdbcTemplate.update("""
                INSERT INTO auth_group_templates (
                    code,
                    name,
                    scope_type,
                    system_managed,
                    built_in,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, code, displayName, scopeType, false, true, "ACTIVE");
        return jdbcTemplate.queryForObject("SELECT id FROM auth_group_templates WHERE code = ?", Long.class, code);
    }

    private Long insertAuthzGroup(Long templateId, String code, String scopeType, Long scopeRefId) {
        jdbcTemplate.update("""
                INSERT INTO auth_groups (
                    template_id,
                    scope_type,
                    scope_ref_id,
                    display_name,
                    managed_by_system,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, templateId, scopeType, scopeRefId, code, false, "ACTIVE");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM auth_groups WHERE template_id = ? AND scope_type = ? AND scope_ref_id = ?",
                Long.class,
                templateId,
                scopeType,
                scopeRefId);
    }

    private void addMember(String token, Long offeringId, Long userId, String roleCode, Long classId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":%s,"memberRole":"%s","teachingClassId":%s,"remark":"seed"}
                                  ]
                                }
                                """.formatted(userId, roleCode, classId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));
    }

    private Long findMemberId(Long offeringId, Long userId, String memberRole) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM course_members
                WHERE offering_id = ?
                  AND user_id = ?
                  AND member_role = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, offeringId, userId, memberRole);
    }

    private Long createAssignment(
            String token,
            Long offeringId,
            Long teachingClassId,
            String title,
            OffsetDateTime openAt,
            OffsetDateTime dueAt,
            int maxSubmissions)
            throws Exception {
        String teachingClassField = teachingClassId == null ? "null" : String.valueOf(teachingClassId);
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"%s",
                                  "description":"任务说明",
                                  "teachingClassId":%s,
                                  "openAt":"%s",
                                  "dueAt":"%s",
                                  "maxSubmissions":%s
                                }
                                """.formatted(
                                        title,
                                        teachingClassField,
                                        OFFSET_DATE_TIME.format(openAt),
                                        OFFSET_DATE_TIME.format(dueAt),
                                        maxSubmissions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
    }

    private void publishAssignment(String token, Long assignmentId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/publish", assignmentId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private String resolveTeacherToken(String token) {
        return latestTeacherToken == null ? token : latestTeacherToken;
    }

    private Long createSubmission(String token, Long assignmentId, String contentText, Long... artifactIds)
            throws Exception {
        String artifactIdsJson = java.util.Arrays.stream(artifactIds)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        MvcResult result = mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"%s",
                                  "artifactIds":[%s]
                                }
                                """.formatted(contentText, artifactIdsJson)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long uploadArtifact(String token, Long assignmentId, String filename, String contentType, String content)
            throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", filename, contentType, content.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/me/assignments/{assignmentId}/submission-artifacts", assignmentId)
                                .file(file)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value(filename))
                .andExpect(jsonPath("$.contentType").value(contentType))
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

    private Long readLong(MvcResult result, String expression) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), expression);
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int queryForCount(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
