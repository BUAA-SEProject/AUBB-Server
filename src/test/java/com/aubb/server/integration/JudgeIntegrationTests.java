package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.modules.judge.application.JudgeMetricsRecorder;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "aubb.judge.go-judge.enabled=true"})
@AutoConfigureMockMvc
@Import(com.aubb.server.TestcontainersConfiguration.class)
class JudgeIntegrationTests extends AbstractRealJudgeIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private String latestTeacherToken;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisIntegrationTestSupport.registerRedisProperties(registry);
    }

    @AfterAll
    static void stopServer() {
        // 真实 go-judge 由 Testcontainers 生命周期管理，这里保留钩子便于后续扩展。
    }

    @BeforeEach
    void setUp() {
        RedisIntegrationTestSupport.flushAll();
        resetJudgeTables(jdbcTemplate, """
                TRUNCATE TABLE
                    audit_logs,
                    auth_sessions,
                    role_bindings,
                    judge_jobs,
                    assignment_judge_cases,
                    assignment_judge_profiles,
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
    void configuredJudgeAssignmentRunsThroughGoJudgeAndSupportsRequeue() throws Exception {
        double successCounterBefore =
                counterValue(JudgeMetricsRecorder.JUDGE_JOB_EXECUTIONS_METRIC, "result", "succeeded");
        long successTimerCountBefore =
                timerCount(JudgeMetricsRecorder.JUDGE_JOB_EXECUTION_DURATION_METRIC, "result", "succeeded");
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "字符串反转实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].passedCaseCount").value(2))
                .andExpect(jsonPath("$[0].totalCaseCount").value(2))
                .andExpect(jsonPath("$[0].score").value(100))
                .andExpect(jsonPath("$[0].maxScore").value(100))
                .andExpect(jsonPath("$[0].resultSummary").value(org.hamcrest.Matchers.containsString("2/2")));

        mockMvc.perform(post("/api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue", submissionId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.triggerType").value("MANUAL_REJUDGE"));

        waitForJudgeJobCount(submissionId, 2);
        waitForLatestJudgeJobTerminal(submissionId);

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(
                        "$[*].triggerType", org.hamcrest.Matchers.containsInAnyOrder("MANUAL_REJUDGE", "AUTO")))
                .andExpect(
                        jsonPath("$[?(@.triggerType=='MANUAL_REJUDGE')].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[?(@.triggerType=='MANUAL_REJUDGE')].verdict")
                        .value("ACCEPTED"));

        assertThat(counterValue(JudgeMetricsRecorder.JUDGE_JOB_EXECUTIONS_METRIC, "result", "succeeded")
                        - successCounterBefore)
                .isEqualTo(2.0d);
        assertThat(timerCount(JudgeMetricsRecorder.JUDGE_JOB_EXECUTION_DURATION_METRIC, "result", "succeeded")
                        - successTimerCountBefore)
                .isEqualTo(2L);
        assertThat(meterRegistry
                        .get(JudgeMetricsRecorder.JUDGE_QUEUE_DEPTH_METRIC)
                        .gauge()
                        .value())
                .isGreaterThanOrEqualTo(0.0d);
    }

    @Test
    void classScopedStudentCanReadJudgeJobsForOfferingWideAssignment() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-OFFERING", "公共判题班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, null, "公共判题任务");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);

        MvcResult listResult = mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        Long judgeJobId = readLong(listResult, "$[0].id");

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMetadata.mode").value("LEGACY_ASSIGNMENT"))
                .andExpect(jsonPath("$.caseReports.length()").value(2));
    }

    @Test
    void offeringTaCannotRequeueJudgeWithoutExplicitRejudgeGrantAndDeniedAuditShouldBeRecorded() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");
        insertUser(2L, "offering-ta", "Offering TA", "offering-ta@example.com");
        String offeringTaToken = login("offering-ta", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-RQ-DENY", "重判限制班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        grantRoleBinding(5L, "offering_ta", "offering", offeringId);
        studentToken = login("student-a", "Password123");
        offeringTaToken = login("offering-ta", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "重判权限测试");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);

        mockMvc.perform(post("/api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue", submissionId)
                        .header("Authorization", "Bearer " + offeringTaToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'JUDGE_REJUDGE' AND decision = 'DENY'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT scope_type FROM audit_logs WHERE action = 'JUDGE_REJUDGE' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("class");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'permissionCode' FROM audit_logs WHERE action = 'JUDGE_REJUDGE' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("judge.rejudge");
    }

    @Test
    void wrongAnswerBecomesSuccessfulJudgeJobWithWrongAnswerVerdict() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "大小写转换实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input())");
        waitForLatestJudgeJobTerminal(submissionId);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$[0].passedCaseCount").value(0))
                .andExpect(jsonPath("$[0].score").value(0))
                .andExpect(jsonPath("$[0].maxScore").value(100));
    }

    @Test
    void runtimeErrorBecomesSuccessfulJudgeJobWithRuntimeSummary() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "运行失败实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "raise RuntimeError('boom')");
        waitForLatestJudgeJobTerminal(submissionId);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$[0].passedCaseCount").value(0))
                .andExpect(jsonPath("$[0].score").value(0))
                .andExpect(jsonPath("$[0].stderrExcerpt").value(org.hamcrest.Matchers.containsString("Traceback")))
                .andExpect(jsonPath("$[0].resultSummary").value(org.hamcrest.Matchers.containsString("程序运行失败")));
    }

    @Test
    void syntaxErrorBecomesSuccessfulJudgeJobWithCompileFailureSummary() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "失败场景实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1]");
        waitForLatestJudgeJobTerminal(submissionId);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$[0].stderrExcerpt").value(org.hamcrest.Matchers.containsString("SyntaxError")))
                .andExpect(jsonPath("$[0].resultSummary").value(org.hamcrest.Matchers.containsString("编译失败")));
    }

    @Test
    void legacyJudgeJobReportMasksHiddenFieldsForTeacherWithoutGrant() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "报告接口实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);

        MvcResult listResult = mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].detailReportAvailable").value(true))
                .andReturn();
        Long judgeJobId = readLong(listResult, "$[0].id");

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMetadata.mode").value("LEGACY_ASSIGNMENT"))
                .andExpect(jsonPath("$.executionMetadata.programmingLanguage").value("PYTHON3"))
                .andExpect(jsonPath("$.caseReports.length()").value(2))
                .andExpect(jsonPath("$.caseReports[0].stdoutText").value("cba\n"))
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").doesNotExist());

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMetadata.mode").value("LEGACY_ASSIGNMENT"))
                .andExpect(jsonPath("$.stdoutText").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].stdoutText").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].runCommand").doesNotExist());
    }

    @Test
    void explicitJudgeViewHiddenGrantCanReadSensitiveTeacherJudgeReportAndWritesAudit() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-HIDDEN", "隐藏权限班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "隐藏评测报告实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);
        Long judgeJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM judge_jobs WHERE submission_id = ? ORDER BY id DESC LIMIT 1", Long.class, submissionId);

        jdbcTemplate.update("""
                INSERT INTO roles (code, name, description, role_category, scope_type, is_builtin, status)
                VALUES ('judge-hidden-reader', '隐藏评测查看者', '允许查看隐藏测试详情', 'TEACHING', 'offering', FALSE, 'ACTIVE')
                """);
        Long hiddenRoleId =
                jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = 'judge-hidden-reader'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT ?, id
                FROM permissions
                WHERE code = 'judge.view_hidden'
                """, hiddenRoleId);
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
                """, 3L, hiddenRoleId, offeringId);
        latestTeacherToken = login("teacher-main", "Password123");

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").value("cba\n"))
                .andExpect(jsonPath("$.caseReports[0].runCommand[0]").value("/usr/bin/python3"));

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report/download", judgeJobId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").value("cba\n"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'JUDGE_HIDDEN_READ' AND decision = 'ALLOW'",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'permissionCode' FROM audit_logs WHERE action = 'JUDGE_HIDDEN_READ' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("judge.view_hidden");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'channel' FROM audit_logs WHERE action = 'JUDGE_HIDDEN_READ' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("download");
    }

    @Test
    void submissionReadOnlyBindingCanReadTeacherJudgeReportButSensitiveFieldsAreMasked() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");
        insertUser(2L, "report-reader", "Report Reader", "report-reader@example.com");
        String reportReaderToken = login("report-reader", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-MASK", "脱敏班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "报告字段脱敏实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);

        jdbcTemplate.update("""
                INSERT INTO roles (code, name, description, role_category, scope_type, is_builtin, status)
                VALUES ('judge-report-reader', '评测报告只读者', '仅允许查看评测对象元数据', 'TEACHING', 'offering', FALSE, 'ACTIVE')
                """);
        Long roleId =
                jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = 'judge-report-reader'", Long.class);
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
                """, 5L, roleId, offeringId);
        reportReaderToken = login("report-reader", "Password123");

        MvcResult listResult = mockMvc.perform(
                        get("/api/v1/teacher/submissions/{submissionId}/judge-jobs", submissionId)
                                .header("Authorization", "Bearer " + reportReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        Long judgeJobId = readLong(listResult, "$[0].id");

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + reportReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stdoutText").doesNotExist())
                .andExpect(jsonPath("$.stderrText").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].stdoutText").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].runCommand").doesNotExist());

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report/download", judgeJobId)
                        .header("Authorization", "Bearer " + reportReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stdoutText").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].stdoutText").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").doesNotExist());
    }

    @Test
    void activeStudentWithoutRoleBindingsCannotReadOwnJudgeJobs() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-HIS", "判题历史班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "判题历史收口实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);
        Long judgeJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM judge_jobs WHERE submission_id = ? ORDER BY id DESC LIMIT 1", Long.class, submissionId);

        jdbcTemplate.update("DELETE FROM role_bindings WHERE user_id = ?", 4L);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void judgeJobReportUsesCacheAfterFirstSuccessfulRead() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-CACHE", "缓存班", 2024);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createJudgeAssignment(teacherToken, offeringId, classId, "缓存报告实验");
        publishAssignment(teacherToken, assignmentId);

        Long submissionId = createSubmission(studentToken, assignmentId, "print(input()[::-1])");
        waitForLatestJudgeJobTerminal(submissionId);

        MvcResult listResult = mockMvc.perform(get("/api/v1/me/submissions/{submissionId}/judge-jobs", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long judgeJobId = readLong(listResult, "$[0].id");

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReports.length()").value(2));

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReports.length()").value(2));

        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andExpect(result -> assertThat(
                        result.getResponse().getContentAsString())
                .contains("aubb_cache_operations_total{cache=\"judgeJobReport\",operation=\"get\",result=\"hit\"}"));
    }

    private void waitForLatestJudgeJobTerminal(Long submissionId) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        String latestStatus = null;
        String latestErrorMessage = null;
        String latestResultSummary = null;
        while (System.currentTimeMillis() < deadline) {
            latestStatus = jdbcTemplate.query(
                    "SELECT status FROM judge_jobs WHERE submission_id = ? ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null,
                    submissionId);
            latestErrorMessage = jdbcTemplate.query(
                    "SELECT error_message FROM judge_jobs WHERE submission_id = ? ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null,
                    submissionId);
            latestResultSummary = jdbcTemplate.query(
                    "SELECT result_summary FROM judge_jobs WHERE submission_id = ? ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null,
                    submissionId);
            if ("SUCCEEDED".equals(latestStatus) || "FAILED".equals(latestStatus)) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("评测任务未在预期时间内进入终态，status=%s, error=%s, summary=%s"
                .formatted(latestStatus, latestErrorMessage, latestResultSummary));
    }

    private void waitForJudgeJobCount(Long submissionId, int expectedCount) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM judge_jobs WHERE submission_id = ?", Integer.class, submissionId);
            if (count != null && count == expectedCount) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("评测任务数量未在预期时间内达到 " + expectedCount);
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
                        .header("Authorization", "Bearer " + resolveTeacherToken(token))
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
        refreshTeacherToken("class.manage", "member.manage");
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

    private void grantRoleBinding(Long userId, String roleCode, String scopeType, Long scopeId) {
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
                )
                SELECT ?, id, ?, ?, '{}'::jsonb, 'ACTIVE', now(), NULL, NULL, 'MANUAL', 0
                FROM roles
                WHERE code = ?
                """, userId, scopeType, scopeId, roleCode);
    }

    private Long createJudgeAssignment(String token, Long offeringId, Long teachingClassId, String title)
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
                                  "maxSubmissions":3,
                                  "judgeConfig":{
                                    "language":"PYTHON3",
                                    "timeLimitMs":1000,
                                    "memoryLimitMb":128,
                                    "outputLimitKb":64,
                                    "testCases":[
                                      {"stdinText":"abc\\n","expectedStdout":"cba\\n","score":60},
                                      {"stdinText":"hello\\n","expectedStdout":"olleh\\n","score":40}
                                    ]
                                  }
                                }
                                """.formatted(
                                        title,
                                        teachingClassField,
                                        OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.ofHours(8))
                                                .minusDays(1)),
                                        OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.ofHours(8))
                                                .plusDays(3)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.judgeConfig.enabled").value(true))
                .andExpect(jsonPath("$.judgeConfig.language").value("PYTHON3"))
                .andExpect(jsonPath("$.judgeConfig.caseCount").value(2))
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
        if (latestTeacherToken == null) {
            return token;
        }
        String[] segments = token.split("\\.");
        if (segments.length < 2) {
            return token;
        }
        String payload = new String(
                java.util.Base64.getUrlDecoder().decode(segments[1]), java.nio.charset.StandardCharsets.UTF_8);
        String subject = JsonPath.read(payload, "$.sub");
        return "teacher-main".equals(subject) ? latestTeacherToken : token;
    }

    private Long createSubmission(String token, Long assignmentId, String contentText) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"%s",
                                  "artifactIds":[]
                                }
                                """.formatted(escapeJson(contentText))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
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

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private double counterValue(String name, String tagKey, String tagValue) {
        return meterRegistry.get(name).tag(tagKey, tagValue).counter().count();
    }

    private long timerCount(String name, String tagKey, String tagValue) {
        return meterRegistry.get(name).tag(tagKey, tagValue).timer().count();
    }

    private void refreshTeacherToken(String... expectedPermissionCodes) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
        while (true) {
            String candidate = login("teacher-main", "Password123");
            if (isRoleBindingSnapshotReady(candidate)
                    && tokenContainsAllPermissions(candidate, expectedPermissionCodes)) {
                latestTeacherToken = candidate;
                return;
            }
            if (System.nanoTime() >= deadline) {
                latestTeacherToken = candidate;
                assertThat(isRoleBindingSnapshotReady(candidate)).isTrue();
                assertThat(readPermissionCodes(candidate)).contains(expectedPermissionCodes);
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待教师权限快照收敛时被中断", exception);
            }
        }
    }

    private boolean isRoleBindingSnapshotReady(String token) {
        return Boolean.TRUE.equals(readTokenClaim(token, "$.roleBindingSnapshot"));
    }

    private boolean tokenContainsAllPermissions(String token, String... expectedPermissionCodes) {
        return readPermissionCodes(token).containsAll(java.util.List.of(expectedPermissionCodes));
    }

    private java.util.List<String> readPermissionCodes(String token) {
        java.util.List<String> permissionCodes = readTokenClaim(token, "$.permissionCodes");
        return permissionCodes == null ? java.util.List.of() : permissionCodes;
    }

    private <T> T readTokenClaim(String token, String path) {
        String[] segments = token.split("\\.");
        if (segments.length < 2) {
            return null;
        }
        String payload = new String(
                java.util.Base64.getUrlDecoder().decode(segments[1]), java.nio.charset.StandardCharsets.UTF_8);
        return JsonPath.read(payload, path);
    }
}
