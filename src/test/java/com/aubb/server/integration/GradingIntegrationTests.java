package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.modules.grading.application.GradingMetricsRecorder;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
class GradingIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-grading-test-assets";

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
    private MeterRegistry meterRegistry;

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
                    grade_appeals,
                    judge_jobs,
                    submission_answers,
                    submission_artifacts,
                    submissions,
                    assignment_question_options,
                    assignment_questions,
                    assignment_sections,
                    question_bank_question_options,
                    question_bank_questions,
                    assignment_judge_cases,
                    assignment_judge_profiles,
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
        insertUser(2L, "ta-a", "TA A", "ta-a@example.com");
        insertUser(2L, "ta-b", "TA B", "ta-b@example.com");
        insertUser(2L, "student-a", "Student A", "student-a@example.com");
        insertUser(2L, "class-instructor", "Class Instructor", "class-instructor@example.com");

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
    void teacherAndTaGradeStructuredSubmissionAndPublishGrades() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taToken = login("ta-a", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        addMember(teacherToken, offeringId, 4L, "TA", classId);
        taToken = login("ta-a", "Password123");
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");

        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].autoScore").value(10))
                .andExpect(jsonPath("$.answers[0].finalScore").value(10))
                .andExpect(jsonPath("$.answers[1].manualScore").doesNotExist())
                .andExpect(jsonPath("$.answers[1].feedbackText").doesNotExist())
                .andExpect(jsonPath("$.scoreSummary.autoScoredScore").value(10))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(10))
                .andExpect(jsonPath("$.scoreSummary.gradePublished").value(false))
                .andExpect(jsonPath("$.scoreSummary.pendingManualCount").value(2))
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/grades/publish", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(
                                "/api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade",
                                submissionId,
                                shortAnswerId)
                        .header("Authorization", "Bearer " + taToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "score":18,
                                  "feedbackText":"关键点正确，但没有补充按秩合并。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.id").value(shortAnswerId))
                .andExpect(jsonPath("$.answer.manualScore").value(18))
                .andExpect(jsonPath("$.answer.finalScore").value(18))
                .andExpect(jsonPath("$.answer.gradingStatus").value("MANUALLY_GRADED"))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(28))
                .andExpect(jsonPath("$.scoreSummary.fullyGraded").value(false));

        mockMvc.perform(post(
                                "/api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade",
                                submissionId,
                                fileAnswerId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "score":27,
                                  "feedbackText":"报告结构完整，实验现象分析还可更深入。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.id").value(fileAnswerId))
                .andExpect(jsonPath("$.answer.manualScore").value(27))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(55))
                .andExpect(jsonPath("$.scoreSummary.fullyGraded").value(true));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].manualScore").value(18))
                .andExpect(jsonPath("$.answers[2].manualScore").value(27))
                .andExpect(jsonPath("$.scoreSummary.manualScoredScore").value(45))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(55))
                .andExpect(jsonPath("$.scoreSummary.gradePublished").value(false));

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].gradingStatus").value("MANUALLY_GRADED"))
                .andExpect(jsonPath("$.answers[1].manualScore").doesNotExist())
                .andExpect(jsonPath("$.answers[1].feedbackText").doesNotExist())
                .andExpect(jsonPath("$.scoreSummary.autoScoredScore").value(10))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(10))
                .andExpect(jsonPath("$.scoreSummary.gradePublished").value(false))
                .andExpect(jsonPath("$.scoreSummary.fullyGraded").value(true));

        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/grades/publish", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.publishedByUserId").value(3))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 6
                                  AND n.type = 'ASSIGNMENT_GRADES_PUBLISHED'
                                """), 1);

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].manualScore").value(18))
                .andExpect(jsonPath("$.answers[1].feedbackText").value("关键点正确，但没有补充按秩合并。"))
                .andExpect(jsonPath("$.answers[2].manualScore").value(27))
                .andExpect(jsonPath("$.answers[2].feedbackText").value("报告结构完整，实验现象分析还可更深入。"))
                .andExpect(jsonPath("$.scoreSummary.manualScoredScore").value(45))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(55))
                .andExpect(jsonPath("$.scoreSummary.gradePublished").value(true));

        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'SUBMISSION_ANSWER_GRADED'"))
                .isEqualTo(2);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'ASSIGNMENT_GRADES_PUBLISHED'"))
                .isEqualTo(1);
    }

    @Test
    void publishingGradesCreatesSnapshotBatchAndTeacherCanTraceDetails() throws Exception {
        double initialPublicationCounterBefore =
                counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "initial");
        double republishCounterBefore =
                counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "republish");
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        gradeAnswer(teacherToken, submissionId, shortAnswerId, 18, "关键点正确，但没有补充按秩合并。");
        gradeAnswer(teacherToken, submissionId, fileAnswerId, 27, "报告结构完整，实验现象分析还可更深入。");

        MvcResult publishResult = publishGradesForResult(teacherToken, assignmentId);
        Long batchId = readLong(publishResult, "$.snapshotBatchId");

        assertThat(queryForCount("SELECT COUNT(*) FROM grade_publish_snapshot_batches"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM grade_publish_snapshots"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT snapshot_count FROM grade_publish_snapshot_batches WHERE id = ?",
                        Integer.class,
                        batchId))
                .isEqualTo(1);

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/grade-publish-batches", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].batchId").value(batchId))
                .andExpect(jsonPath("$[0].publishSequence").value(1))
                .andExpect(jsonPath("$[0].snapshotCount").value(1))
                .andExpect(jsonPath("$[0].initialPublication").value(true));

        mockMvc.perform(get(
                                "/api/v1/teacher/assignments/{assignmentId}/grade-publish-batches/{batchId}",
                                assignmentId,
                                batchId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.batchId").value(batchId))
                .andExpect(jsonPath("$.batch.publishSequence").value(1))
                .andExpect(jsonPath("$.snapshots.length()").value(1))
                .andExpect(jsonPath("$.snapshots[0].studentUserId").value(6))
                .andExpect(jsonPath("$.snapshots[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$.snapshots[0].totalFinalScore").value(55))
                .andExpect(jsonPath("$.snapshots[0].totalMaxScore").value(60))
                .andExpect(jsonPath("$.snapshots[0].snapshot.scoreSummary.finalScore")
                        .value(55))
                .andExpect(jsonPath("$.snapshots[0].snapshot.scoreSummary.gradePublished")
                        .value(true))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers.length()").value(3))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers[1].feedbackText")
                        .value("关键点正确，但没有补充按秩合并。"))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers[2].feedbackText")
                        .value("报告结构完整，实验现象分析还可更深入。"));

        assertThat(counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "initial")
                        - initialPublicationCounterBefore)
                .isEqualTo(1.0d);
        assertThat(counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "republish")
                        - republishCounterBefore)
                .isEqualTo(0.0d);
    }

    @Test
    void repeatedPublishingCreatesNewSnapshotBatchWithoutResettingInitialPublication() throws Exception {
        double initialPublicationCounterBefore =
                counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "initial");
        double republishCounterBefore =
                counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "republish");
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        gradeAnswer(teacherToken, submissionId, shortAnswerId, 18, "关键点正确，但没有补充按秩合并。");
        gradeAnswer(teacherToken, submissionId, fileAnswerId, 27, "报告结构完整，实验现象分析还可更深入。");

        MvcResult firstPublish = publishGradesForResult(teacherToken, assignmentId);
        Long firstBatchId = readLong(firstPublish, "$.snapshotBatchId");
        String firstPublishedAt = JsonPath.read(firstPublish.getResponse().getContentAsString(), "$.publishedAt");

        gradeAnswer(teacherToken, submissionId, fileAnswerId, 29, "第二次发布前补充了实验分析。");

        MvcResult secondPublish = publishGradesForResult(teacherToken, assignmentId);
        Long secondBatchId = readLong(secondPublish, "$.snapshotBatchId");
        String secondPublishedAt = JsonPath.read(secondPublish.getResponse().getContentAsString(), "$.publishedAt");

        assertThat(firstBatchId).isNotEqualTo(secondBatchId);
        assertThat(OffsetDateTime.parse(secondPublishedAt).toInstant())
                .isEqualTo(OffsetDateTime.parse(firstPublishedAt).toInstant());
        assertThat(queryForCount("SELECT COUNT(*) FROM grade_publish_snapshot_batches"))
                .isEqualTo(2);
        assertThat(queryForCount("SELECT COUNT(*) FROM grade_publish_snapshots"))
                .isEqualTo(2);
        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 6
                                  AND n.type = 'ASSIGNMENT_GRADES_PUBLISHED'
                                """), 1);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'ASSIGNMENT_GRADES_PUBLISHED'"))
                .isEqualTo(2);

        mockMvc.perform(get(
                                "/api/v1/teacher/assignments/{assignmentId}/grade-publish-batches/{batchId}",
                                assignmentId,
                                firstBatchId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshots[0].totalFinalScore").value(55))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers[2].finalScore")
                        .value(27))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers[2].feedbackText")
                        .value("报告结构完整，实验现象分析还可更深入。"));

        mockMvc.perform(get(
                                "/api/v1/teacher/assignments/{assignmentId}/grade-publish-batches/{batchId}",
                                assignmentId,
                                secondBatchId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.publishSequence").value(2))
                .andExpect(jsonPath("$.batch.initialPublication").value(false))
                .andExpect(jsonPath("$.snapshots[0].totalFinalScore").value(57))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers[2].finalScore")
                        .value(29))
                .andExpect(jsonPath("$.snapshots[0].snapshot.answers[2].feedbackText")
                        .value("第二次发布前补充了实验分析。"));

        assertThat(counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "initial")
                        - initialPublicationCounterBefore)
                .isEqualTo(1.0d);
        assertThat(counterValue(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC, "publish_type", "republish")
                        - republishCounterBefore)
                .isEqualTo(1.0d);
    }

    @Test
    void teacherBatchAdjustsMultipleAnswersWithinAssignment() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/grades/batch-adjust", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "adjustments":[
                                    {
                                      "submissionId":%s,
                                      "answerId":%s,
                                      "score":19,
                                      "feedbackText":"简答题补充了启发式合并。"
                                    },
                                    {
                                      "submissionId":%s,
                                      "answerId":%s,
                                      "score":26,
                                      "feedbackText":"报告结构完整，分析略有欠缺。"
                                    }
                                  ]
                                }
                                """.formatted(submissionId, shortAnswerId, submissionId, fileAnswerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].answer.id").value(shortAnswerId))
                .andExpect(jsonPath("$.results[0].answer.manualScore").value(19))
                .andExpect(jsonPath("$.results[1].answer.id").value(fileAnswerId))
                .andExpect(jsonPath("$.results[1].answer.manualScore").value(26))
                .andExpect(jsonPath("$.results[1].scoreSummary.finalScore").value(55))
                .andExpect(jsonPath("$.results[1].scoreSummary.fullyGraded").value(true));

        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'SUBMISSION_ANSWER_GRADED'"))
                .isEqualTo(2);
    }

    @Test
    void classTaBatchAdjustShouldRequireGradeOverrideAndRecordDeniedAudit() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String classTaToken = login("ta-a", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-OVERRIDE", "改分班", 2026);
        addMember(teacherToken, offeringId, 4L, "TA", classId);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        classTaToken = login("ta-a", "Password123");
        studentToken = login("student-a", "Password123");
        grantRoleBinding(4L, "class_ta", "class", classId);

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "override.pdf", "application/pdf", "%PDF-1.7\noverride");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"需要显式改分权限。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");

        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/grades/batch-adjust", assignmentId)
                        .header("Authorization", "Bearer " + classTaToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "adjustments":[
                                    {
                                      "submissionId":%s,
                                      "answerId":%s,
                                      "score":19,
                                      "feedbackText":"班级助教不应直接改分"
                                    }
                                  ]
                                }
                                """.formatted(submissionId, shortAnswerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(queryForCount(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'GRADE_OVERRIDE' AND decision = 'DENY'"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT scope_type FROM audit_logs WHERE action = 'GRADE_OVERRIDE' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("class");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT scope_id FROM audit_logs WHERE action = 'GRADE_OVERRIDE' ORDER BY id DESC LIMIT 1",
                        Long.class))
                .isEqualTo(classId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'permissionCode' FROM audit_logs WHERE action = 'GRADE_OVERRIDE' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("grade.override");
    }

    @Test
    void teacherExportsAndImportsBatchGradeTemplate() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        MvcResult exportResult = mockMvc.perform(
                        get("/api/v1/teacher/assignments/{assignmentId}/grades/import-template", assignmentId)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("assignment-grades-" + assignmentId)))
                .andReturn();

        String exportedCsv = exportResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(exportedCsv)
                .contains(
                        "submissionId,answerId,submissionNo,attemptNo,studentUsername,studentDisplayName,questionTitle,questionType,maxScore,currentScore,currentFeedbackText,newScore,newFeedbackText");
        assertThat(exportedCsv).contains("student-a");
        assertThat(exportedCsv).contains("路径压缩简答");
        assertThat(exportedCsv).contains("实验报告上传");

        MockMultipartFile file = new MockMultipartFile("file", "grades-import.csv", "text/csv", """
                submissionId,answerId,newScore,newFeedbackText
                %s,%s,19,导入模板补分
                %s,%s,27,导入模板批注
                """.formatted(
                        submissionId, shortAnswerId, submissionId, fileAnswerId)
                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/teacher/assignments/{assignmentId}/grades/import", assignmentId)
                        .file(file)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.errors.length()").value(0));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].manualScore").value(19))
                .andExpect(jsonPath("$.answers[1].feedbackText").value("导入模板补分"))
                .andExpect(jsonPath("$.answers[2].manualScore").value(27))
                .andExpect(jsonPath("$.answers[2].feedbackText").value("导入模板批注"))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(56))
                .andExpect(jsonPath("$.scoreSummary.fullyGraded").value(true));

        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'ASSIGNMENT_GRADES_IMPORTED'"))
                .isEqualTo(1);
    }

    @Test
    void gradeImportReturnsRowErrorsAndKeepsSuccessfulRows() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        MockMultipartFile file = new MockMultipartFile("file", "grades-import.csv", "text/csv", """
                submissionId,answerId,newScore,newFeedbackText
                %s,%s,18,批量导入成功
                %s,%s,99,超出题目分值
                """.formatted(
                        submissionId, shortAnswerId, submissionId, fileAnswerId)
                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/teacher/assignments/{assignmentId}/grades/import", assignmentId)
                        .file(file)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(3))
                .andExpect(jsonPath("$.errors[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$.errors[0].answerId").value(fileAnswerId))
                .andExpect(jsonPath("$.errors[0].message").value("人工批改分数超出题目分值范围"));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].manualScore").value(18))
                .andExpect(jsonPath("$.answers[1].feedbackText").value("批量导入成功"))
                .andExpect(jsonPath("$.answers[2].manualScore").doesNotExist())
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(28))
                .andExpect(jsonPath("$.scoreSummary.fullyGraded").value(false));

        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'ASSIGNMENT_GRADES_IMPORTED'"))
                .isEqualTo(1);
    }

    @Test
    void studentCreatesAppealAndTeacherResolvesWithScoreRevision() throws Exception {
        double createdAppealCounterBefore = counterValue(GradingMetricsRecorder.GRADE_APPEALS_CREATED_METRIC);
        double acceptedAppealCounterBefore =
                counterValue(GradingMetricsRecorder.GRADE_APPEALS_REVIEWED_METRIC, "result", "accepted");
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taToken = login("ta-a", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        addMember(teacherToken, offeringId, 4L, "TA", classId);
        taToken = login("ta-a", "Password123");
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        gradeAnswer(taToken, submissionId, shortAnswerId, 18, "关键点正确，但没有补充按秩合并。");
        gradeAnswer(teacherToken, submissionId, fileAnswerId, 27, "报告结构完整，实验现象分析还可更深入。");
        publishGrades(teacherToken, assignmentId);

        MvcResult createAppealResult = mockMvc.perform(post(
                                "/api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals",
                                submissionId,
                                shortAnswerId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "reason":"简答题覆盖了路径压缩和启发式合并，希望补充分数。"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.submissionId").value(submissionId))
                .andExpect(jsonPath("$.submissionAnswerId").value(shortAnswerId))
                .andExpect(jsonPath("$.studentUserId").value(6))
                .andExpect(jsonPath("$.questionTitle").value("路径压缩简答"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentFinalScore").value(18))
                .andReturn();
        Long appealId = readLong(createAppealResult, "$.id");

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/grade-appeals", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appealId))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].currentFinalScore").value(18));

        mockMvc.perform(post("/api/v1/teacher/grade-appeals/{appealId}/review", appealId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision":"ACCEPTED",
                                  "responseText":"同意补分，补充内容有效。",
                                  "revisedScore":20,
                                  "revisedFeedbackText":"复核后按满分计。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appealId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.responseText").value("同意补分，补充内容有效。"))
                .andExpect(jsonPath("$.resolvedScore").value(20))
                .andExpect(jsonPath("$.currentFinalScore").value(20))
                .andExpect(jsonPath("$.answerFeedbackText").value("复核后按满分计。"))
                .andExpect(jsonPath("$.respondedByUserId").value(3))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/me/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].manualScore").value(20))
                .andExpect(jsonPath("$.answers[1].feedbackText").value("复核后按满分计。"))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(57));

        assertThat(queryForCount("SELECT COUNT(*) FROM grade_appeals WHERE status = 'ACCEPTED'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'GRADE_APPEAL_CREATED'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM audit_logs WHERE action = 'GRADE_APPEAL_REVIEWED'"))
                .isEqualTo(1);
        IntegrationTestAwait.awaitCount(() -> queryForCount("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 6
                                  AND n.type = 'GRADE_APPEAL_RESOLVED'
                                """), 1);
        assertThat(counterValue(GradingMetricsRecorder.GRADE_APPEALS_CREATED_METRIC) - createdAppealCounterBefore)
                .isEqualTo(1.0d);
        assertThat(counterValue(GradingMetricsRecorder.GRADE_APPEALS_REVIEWED_METRIC, "result", "accepted")
                        - acceptedAppealCounterBefore)
                .isEqualTo(1.0d);
    }

    @Test
    void duplicateAppealAndUnauthorizedTaReviewAreRejected() throws Exception {
        double createdAppealCounterBefore = counterValue(GradingMetricsRecorder.GRADE_APPEALS_CREATED_METRIC);
        double rejectedAppealCounterBefore =
                counterValue(GradingMetricsRecorder.GRADE_APPEALS_REVIEWED_METRIC, "result", "rejected");
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taToken = login("ta-a", "Password123");
        String otherTaToken = login("ta-b", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        addMember(teacherToken, offeringId, 4L, "TA", classId);
        taToken = login("ta-a", "Password123");
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        gradeAnswer(taToken, submissionId, shortAnswerId, 18, "关键点正确，但没有补充按秩合并。");
        gradeAnswer(teacherToken, submissionId, fileAnswerId, 27, "报告结构完整，实验现象分析还可更深入。");
        publishGrades(teacherToken, assignmentId);

        MvcResult createAppealResult = mockMvc.perform(post(
                                "/api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals",
                                submissionId,
                                shortAnswerId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "reason":"希望重新核对简答题。"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andReturn();
        Long appealId = readLong(createAppealResult, "$.id");

        mockMvc.perform(post(
                                "/api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals",
                                submissionId,
                                shortAnswerId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"重复提交申诉。"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}/grade-appeals", assignmentId)
                        .header("Authorization", "Bearer " + otherTaToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/teacher/grade-appeals/{appealId}/review", appealId)
                        .header("Authorization", "Bearer " + otherTaToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision":"REJECTED",
                                  "responseText":"无权限助教"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(counterValue(GradingMetricsRecorder.GRADE_APPEALS_CREATED_METRIC) - createdAppealCounterBefore)
                .isEqualTo(1.0d);
        assertThat(counterValue(GradingMetricsRecorder.GRADE_APPEALS_REVIEWED_METRIC, "result", "rejected")
                        - rejectedAppealCounterBefore)
                .isEqualTo(0.0d);
    }

    @Test
    void classInstructorCanAcceptAppealWithinOwnedTeachingClass() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String classInstructorToken = login("class-instructor", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classId);
        addMember(teacherToken, offeringId, 7L, "CLASS_INSTRUCTOR", classId);
        classInstructorToken = login("class-instructor", "Password123");
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时压缩父指针。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");
        Long fileAnswerId = readLong(submissionResult, "$.answers[2].id");

        gradeAnswer(classInstructorToken, submissionId, shortAnswerId, 18, "补充按秩合并可更完整。");
        gradeAnswer(classInstructorToken, submissionId, fileAnswerId, 27, "实验报告结构完整。");
        publishGrades(teacherToken, assignmentId);

        MvcResult appealResult = mockMvc.perform(post(
                                "/api/v1/me/submissions/{submissionId}/answers/{answerId}/appeals",
                                submissionId,
                                shortAnswerId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"已补充按秩合并说明，请重新评估。"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long appealId = readLong(appealResult, "$.id");

        mockMvc.perform(post("/api/v1/teacher/grade-appeals/{appealId}/review", appealId)
                        .header("Authorization", "Bearer " + classInstructorToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision":"ACCEPTED",
                                  "responseText":"补充分点有效，予以加分。",
                                  "revisedScore":20,
                                  "revisedFeedbackText":"复核后按满分计。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.resolvedScore").value(20))
                .andExpect(jsonPath("$.currentFinalScore").value(20))
                .andExpect(jsonPath("$.respondedByUserId").value(7));
    }

    @Test
    void teachingAssistantCannotGradeSubmissionOutsideOwnedClass() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taToken = login("ta-b", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 5L, "TA", classBId);
        taToken = login("ta-b", "Password123");
        studentToken = login("student-a", "Password123");

        Long assignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classAId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");
        Long fileQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[1].id");
        Long artifactId =
                uploadArtifact(studentToken, assignmentId, "report.pdf", "application/pdf", "%PDF-1.7\nreport");

        MvcResult submissionResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"需要在路径查找时压缩父链。"},
                                    {"assignmentQuestionId":%s,"artifactIds":[%s]}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId, fileQuestionId, artifactId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long shortAnswerId = readLong(submissionResult, "$.answers[1].id");

        mockMvc.perform(post(
                                "/api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade",
                                submissionId,
                                shortAnswerId)
                        .header("Authorization", "Bearer " + taToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "score":16,
                                  "feedbackText":"越权批改"
                                }
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
                                  "courseCode":"CS201",
                                  "courseName":"算法设计",
                                  "courseType":"REQUIRED",
                                  "credit":3.0,
                                  "totalHours":48,
                                  "departmentUnitId":2,
                                  "description":"算法课程"
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
                                  "offeringCode":"CS201-2026SP-01",
                                  "offeringName":"算法设计（2026春）",
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
        return readLong(result, "$.id");
    }

    private Long createTeachingClass(String token, Long offeringId, String classCode, String className, int entryYear)
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
                                }
                                """.formatted(classCode, className, entryYear)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private void addMember(String token, Long offeringId, Long userId, String roleCode, Long classId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + token)
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

    private Long createGradableStructuredAssignment(String token, Long offeringId, Long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"结构化批改作业",
                                  "description":"客观题 + 简答题 + 文件题",
                                  "teachingClassId":%s,
                                  "openAt":"2026-04-01T08:00:00+08:00",
                                  "dueAt":"2026-04-30T23:59:59+08:00",
                                  "maxSubmissions":2,
                                  "paper":{
                                    "sections":[
                                      {
                                        "title":"客观题",
                                        "questions":[
                                          {
                                            "title":"复杂度单选",
                                            "prompt":"并查集启发式合并后，单次均摊复杂度趋近于？",
                                            "questionType":"SINGLE_CHOICE",
                                            "score":10,
                                            "options":[
                                              {"optionKey":"A","content":"近似常数","correct":true},
                                              {"optionKey":"B","content":"O(log n)","correct":false},
                                              {"optionKey":"C","content":"O(n)","correct":false}
                                            ]
                                          }
                                        ]
                                      },
                                      {
                                        "title":"人工题",
                                        "questions":[
                                          {
                                            "title":"路径压缩简答",
                                            "prompt":"说明路径压缩的核心思想。",
                                            "questionType":"SHORT_ANSWER",
                                            "score":20,
                                            "config":{"referenceAnswer":"查找时递归压缩父指针。"}
                                          },
                                          {
                                            "title":"实验报告上传",
                                            "prompt":"上传本次实验 PDF 报告。",
                                            "questionType":"FILE_UPLOAD",
                                            "score":30,
                                            "config":{
                                              "maxFileCount":1,
                                              "maxFileSizeMb":20,
                                              "acceptedExtensions":["pdf"]
                                            }
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.formatted(classId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private void publishAssignment(String token, Long assignmentId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/publish", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private void publishGrades(String token, Long assignmentId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/grades/publish", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId));
    }

    private MvcResult publishGradesForResult(String token, Long assignmentId) throws Exception {
        return mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/grades/publish", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.snapshotBatchId").isNumber())
                .andExpect(jsonPath("$.snapshotPublishSequence").isNumber())
                .andExpect(jsonPath("$.snapshotCapturedAt").isNotEmpty())
                .andExpect(jsonPath("$.snapshotCount").isNumber())
                .andReturn();
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
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();
        return readLong(result, "$.id");
    }

    private void gradeAnswer(String token, Long submissionId, Long answerId, int score, String feedbackText)
            throws Exception {
        mockMvc.perform(post(
                                "/api/v1/teacher/submissions/{submissionId}/answers/{answerId}/grade",
                                submissionId,
                                answerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "score":%s,
                                  "feedbackText":"%s"
                                }
                                """.formatted(score, feedbackText)))
                .andExpect(status().isOk());
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

    private double counterValue(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private double counterValue(String name, String tagKey, String tagValue) {
        return meterRegistry.get(name).tag(tagKey, tagValue).counter().count();
    }
}
