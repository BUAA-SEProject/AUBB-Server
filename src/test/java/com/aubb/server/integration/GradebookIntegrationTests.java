package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class GradebookIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
                    programming_sample_runs,
                    judge_jobs,
                    programming_workspaces,
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
    void teacherReadsOfferingGradebookUsingLatestSubmissionAndApplicableAssignments() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/teacher/course-offerings/{offeringId}/gradebook", scenario.offeringId())
                                .header("Authorization", "Bearer " + scenario.teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.assignmentCount").value(2))
                .andExpect(jsonPath("$.summary.studentCount").value(2))
                .andExpect(jsonPath("$.summary.submittedCount").value(3))
                .andExpect(jsonPath("$.summary.fullyGradedCount").value(3))
                .andExpect(jsonPath("$.summary.publishedCount").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.items[0].username")).isEqualTo("student-a");
        assertThat(JsonPath.<String>read(body, "$.items[0].teachingClassName")).isEqualTo("A班");
        assertThat(JsonPath.<Integer>read(body, "$.items[0].submittedAssignmentCount"))
                .isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].totalFinalScore")).isEqualTo(38);
        assertThat(JsonPath.<Double>read(body, "$.items[0].totalWeightedScore")).isEqualTo(96.0);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].totalWeight")).isEqualTo(100);
        assertThat(JsonPath.<Double>read(body, "$.items[0].weightedScoreRate")).isEqualTo(0.96);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].grades[0].latestAttemptNo"))
                .isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.items[0].grades[0].finalScore"))
                .isEqualTo(10);
        assertThat(JsonPath.<Double>read(body, "$.items[0].grades[0].weightedScore"))
                .isEqualTo(40.0);
        assertThat(JsonPath.<Boolean>read(body, "$.items[0].grades[1].applicable"))
                .isTrue();
        assertThat(JsonPath.<Integer>read(body, "$.items[0].grades[1].finalScore"))
                .isEqualTo(28);
        assertThat(JsonPath.<Double>read(body, "$.items[0].grades[1].weightedScore"))
                .isEqualTo(56.0);
        assertThat(JsonPath.<Integer>read(body, "$.assignmentColumns[0].gradeWeight"))
                .isEqualTo(40);
        assertThat(JsonPath.<Integer>read(body, "$.assignmentColumns[1].gradeWeight"))
                .isEqualTo(60);
        assertThat(JsonPath.<String>read(body, "$.items[1].username")).isEqualTo("student-b");
        assertThat(JsonPath.<Integer>read(body, "$.items[1].submittedAssignmentCount"))
                .isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.items[1].totalFinalScore")).isEqualTo(10);
        assertThat(JsonPath.<Double>read(body, "$.items[1].totalWeightedScore")).isEqualTo(40.0);
        assertThat(JsonPath.<Integer>read(body, "$.items[1].totalWeight")).isEqualTo(40);
        assertThat(JsonPath.<Double>read(body, "$.items[1].weightedScoreRate")).isEqualTo(1.0);
        assertThat(JsonPath.<Boolean>read(body, "$.items[1].grades[1].applicable"))
                .isFalse();
        assertThat(JsonPath.<Boolean>read(body, "$.items[1].grades[1].submitted"))
                .isFalse();
    }

    @Test
    void taCanReadOwnedClassGradebookButCannotReadOtherClass() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        mockMvc.perform(get("/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook", scenario.classAId())
                        .header("Authorization", "Bearer " + scenario.taAToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.assignmentCount").value(2))
                .andExpect(jsonPath("$.summary.studentCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].username").value("student-a"))
                .andExpect(jsonPath("$.items[0].grades[0].submitted").value(true))
                .andExpect(jsonPath("$.items[0].grades[1].submitted").value(true));

        mockMvc.perform(get("/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook", scenario.classAId())
                        .header("Authorization", "Bearer " + scenario.taBToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherReadsSingleStudentGradebookDetail() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        mockMvc.perform(get(
                                "/api/v1/teacher/course-offerings/{offeringId}/students/{studentUserId}/gradebook",
                                scenario.offeringId(),
                                scenario.studentAUserId())
                        .header("Authorization", "Bearer " + scenario.teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.userId").value(scenario.studentAUserId()))
                .andExpect(jsonPath("$.student.username").value("student-a"))
                .andExpect(jsonPath("$.summary.assignmentCount").value(2))
                .andExpect(jsonPath("$.summary.submittedCount").value(2))
                .andExpect(jsonPath("$.summary.totalFinalScore").value(38))
                .andExpect(jsonPath("$.summary.totalWeightedScore").value(96.0))
                .andExpect(jsonPath("$.summary.totalWeight").value(100))
                .andExpect(jsonPath("$.summary.weightedScoreRate").value(0.96))
                .andExpect(jsonPath("$.assignments.length()").value(2))
                .andExpect(jsonPath("$.assignments[0].assignment.gradeWeight").value(40))
                .andExpect(jsonPath("$.assignments[0].grade.latestAttemptNo").value(2))
                .andExpect(jsonPath("$.assignments[0].grade.finalScore").value(10))
                .andExpect(jsonPath("$.assignments[0].grade.weightedScore").value(40.0))
                .andExpect(jsonPath("$.assignments[1].assignment.gradeWeight").value(60))
                .andExpect(jsonPath("$.assignments[1].grade.finalScore").value(28))
                .andExpect(jsonPath("$.assignments[1].grade.weightedScore").value(56.0))
                .andExpect(jsonPath("$.assignments[1].grade.gradePublished").value(true));
    }

    @Test
    void teacherExportsOfferingGradebookAsCsv() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/teacher/course-offerings/{offeringId}/gradebook/export", scenario.offeringId())
                                .header("Authorization", "Bearer " + scenario.teacherToken()))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment;")
                .contains("gradebook-offering-%d".formatted(scenario.offeringId()));
        assertThat(csv)
                .contains(
                        "username,displayName,teachingClassCode,teachingClassName,totalFinalScore,totalMaxScore,totalWeightedScore,totalWeight,weightedScoreRate,submittedAssignmentCount,gradedAssignmentCount");
        assertThat(csv).contains("课程公共客观题-gradeWeight");
        assertThat(csv).contains("结构化批改作业 [A班]-applicable");
        assertThat(csv).contains("student-a,Student A,CLS-A,A班,38,40,96.0,100,0.96,2,2");
        assertThat(csv).contains("student-b,Student B,CLS-B,B班,10,10,40.0,40,1.0,1,1");
    }

    @Test
    void taExportsOwnedClassGradebookAsCsvButCannotExportOtherClass() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/export", scenario.classAId())
                                .header("Authorization", "Bearer " + scenario.taAToken()))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("student-a,Student A,CLS-A,A班,38,40,96.0,100,0.96,2,2");
        assertThat(csv).doesNotContain("student-b,Student B");

        mockMvc.perform(get("/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/export", scenario.classAId())
                        .header("Authorization", "Bearer " + scenario.taBToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherReadsOfferingGradebookReportWithAssignmentAndClassStats() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/gradebook/report", scenario.offeringId())
                        .header("Authorization", "Bearer " + scenario.teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.assignmentCount").value(2))
                .andExpect(jsonPath("$.overview.studentCount").value(2))
                .andExpect(jsonPath("$.overview.applicableGradeCount").value(3))
                .andExpect(jsonPath("$.overview.submittedCount").value(3))
                .andExpect(jsonPath("$.overview.submissionRate").value(1.0))
                .andExpect(jsonPath("$.overview.averageTotalFinalScore").value(24.0))
                .andExpect(jsonPath("$.overview.averageTotalScoreRate").value(0.96))
                .andExpect(jsonPath("$.overview.averageTotalWeightedScore").value(68.0))
                .andExpect(jsonPath("$.overview.averageWeightedScoreRate").value(0.9714))
                .andExpect(jsonPath("$.assignments.length()").value(2))
                .andExpect(jsonPath("$.assignments[0].title").value("课程公共客观题"))
                .andExpect(jsonPath("$.assignments[0].gradeWeight").value(40))
                .andExpect(jsonPath("$.assignments[0].submittedStudentCount").value(2))
                .andExpect(
                        jsonPath("$.assignments[0].averageSubmittedFinalScore").value(10.0))
                .andExpect(jsonPath("$.assignments[0].averageSubmittedWeightedScore")
                        .value(40.0))
                .andExpect(jsonPath("$.assignments[1].title").value("结构化批改作业"))
                .andExpect(jsonPath("$.assignments[1].gradeWeight").value(60))
                .andExpect(jsonPath("$.assignments[1].submittedStudentCount").value(1))
                .andExpect(
                        jsonPath("$.assignments[1].averageSubmittedScoreRate").value(0.9333))
                .andExpect(jsonPath("$.assignments[1].averageSubmittedWeightedScore")
                        .value(56.0))
                .andExpect(jsonPath("$.teachingClasses.length()").value(2))
                .andExpect(jsonPath("$.teachingClasses[0].teachingClassCode").value("CLS-A"))
                .andExpect(
                        jsonPath("$.teachingClasses[0].averageTotalFinalScore").value(38.0))
                .andExpect(jsonPath("$.teachingClasses[0].averageTotalWeightedScore")
                        .value(96.0))
                .andExpect(jsonPath("$.teachingClasses[0].averageWeightedScoreRate")
                        .value(0.96))
                .andExpect(jsonPath("$.teachingClasses[1].teachingClassCode").value("CLS-B"))
                .andExpect(
                        jsonPath("$.teachingClasses[1].averageTotalScoreRate").value(1.0))
                .andExpect(jsonPath("$.teachingClasses[1].averageTotalWeightedScore")
                        .value(40.0))
                .andExpect(jsonPath("$.teachingClasses[1].averageWeightedScoreRate")
                        .value(1.0));
    }

    @Test
    void taCanReadOwnedClassGradebookReportButCannotReadOtherClassReport() throws Exception {
        GradebookScenario scenario = seedGradebookScenario();

        mockMvc.perform(get("/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/report", scenario.classAId())
                        .header("Authorization", "Bearer " + scenario.taAToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.studentCount").value(1))
                .andExpect(jsonPath("$.overview.submittedCount").value(2))
                .andExpect(jsonPath("$.overview.averageTotalWeightedScore").value(96.0))
                .andExpect(jsonPath("$.overview.averageWeightedScoreRate").value(0.96))
                .andExpect(jsonPath("$.teachingClasses.length()").value(0))
                .andExpect(jsonPath("$.assignments[0].submittedStudentCount").value(1))
                .andExpect(jsonPath("$.assignments[1].submittedStudentCount").value(1));

        mockMvc.perform(get("/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/report", scenario.classAId())
                        .header("Authorization", "Bearer " + scenario.taBToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentReadsOwnGradebookAndUnpublishedManualScoresRemainHidden() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taAToken = login("ta-a", "Password123");
        String studentAToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 4L, "TA", classAId);

        Long courseWideAssignmentId = createObjectiveAssignment(teacherToken, offeringId, null, "课程公共客观题", 40);
        publishAssignment(teacherToken, courseWideAssignmentId);
        submitObjectiveAssignment(studentAToken, courseWideAssignmentId, "A");
        publishGrades(teacherToken, courseWideAssignmentId);

        Long classAssignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classAId, 60);
        publishAssignment(teacherToken, classAssignmentId);
        GradeableSubmission gradeableSubmission = submitGradableAssignment(studentAToken, classAssignmentId);
        gradeAnswer(
                taAToken,
                gradeableSubmission.submissionId(),
                gradeableSubmission.shortAnswerId(),
                18,
                "关键点正确，但没有补充按秩合并。");

        mockMvc.perform(get("/api/v1/me/course-offerings/{offeringId}/gradebook", offeringId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.userId").value(6))
                .andExpect(jsonPath("$.student.username").value("student-a"))
                .andExpect(jsonPath("$.summary.assignmentCount").value(2))
                .andExpect(jsonPath("$.summary.submittedCount").value(2))
                .andExpect(jsonPath("$.summary.gradedCount").value(2))
                .andExpect(jsonPath("$.summary.totalFinalScore").value(20))
                .andExpect(jsonPath("$.summary.totalMaxScore").value(40))
                .andExpect(jsonPath("$.summary.totalWeightedScore").value(60.0))
                .andExpect(jsonPath("$.summary.totalWeight").value(100))
                .andExpect(jsonPath("$.summary.weightedScoreRate").value(0.6))
                .andExpect(jsonPath("$.assignments.length()").value(2))
                .andExpect(jsonPath("$.assignments[0].grade.gradePublished").value(true))
                .andExpect(jsonPath("$.assignments[0].grade.finalScore").value(10))
                .andExpect(jsonPath("$.assignments[0].grade.weightedScore").value(40.0))
                .andExpect(jsonPath("$.assignments[1].grade.gradePublished").value(false))
                .andExpect(jsonPath("$.assignments[1].grade.finalScore").value(10))
                .andExpect(jsonPath("$.assignments[1].grade.weightedScore").value(20.0))
                .andExpect(jsonPath("$.assignments[1].grade.scoreSummary.manualScoredScore")
                        .doesNotExist());

        mockMvc.perform(get("/api/v1/me/course-offerings/{offeringId}/gradebook", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    private GradebookScenario seedGradebookScenario() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taAToken = login("ta-a", "Password123");
        String taBToken = login("ta-b", "Password123");
        String studentAToken = login("student-a", "Password123");
        String studentBToken = login("student-b", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classAId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        Long classBId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B班", 2026);
        addMember(teacherToken, offeringId, 6L, "STUDENT", classAId);
        addMember(teacherToken, offeringId, 7L, "STUDENT", classBId);
        addMember(teacherToken, offeringId, 4L, "TA", classAId);
        addMember(teacherToken, offeringId, 5L, "TA", classBId);

        Long courseWideAssignmentId = createObjectiveAssignment(teacherToken, offeringId, null, "课程公共客观题", 40);
        publishAssignment(teacherToken, courseWideAssignmentId);
        submitObjectiveAssignment(studentAToken, courseWideAssignmentId, "B");
        submitObjectiveAssignment(studentAToken, courseWideAssignmentId, "A");
        submitObjectiveAssignment(studentBToken, courseWideAssignmentId, "A");
        publishGrades(teacherToken, courseWideAssignmentId);

        Long classAssignmentId = createGradableStructuredAssignment(teacherToken, offeringId, classAId, 60);
        publishAssignment(teacherToken, classAssignmentId);
        GradeableSubmission gradeableSubmission = submitGradableAssignment(studentAToken, classAssignmentId);
        gradeAnswer(
                taAToken,
                gradeableSubmission.submissionId(),
                gradeableSubmission.shortAnswerId(),
                18,
                "关键点正确，但没有补充按秩合并。");
        publishGrades(teacherToken, classAssignmentId);

        return new GradebookScenario(
                teacherToken,
                taAToken,
                taBToken,
                offeringId,
                classAId,
                classBId,
                6L,
                7L,
                courseWideAssignmentId,
                classAssignmentId);
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

    private Long createObjectiveAssignment(String token, Long offeringId, Long classId, String title, int gradeWeight)
            throws Exception {
        String teachingClassValue = classId == null ? "null" : String.valueOf(classId);
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"%s",
                                  "description":"单选题自动评分",
                                  "teachingClassId":%s,
                                  "openAt":"2026-04-01T08:00:00+08:00",
                                  "dueAt":"2026-04-30T23:59:59+08:00",
                                  "maxSubmissions":2,
                                  "gradeWeight":%s,
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
                                      }
                                    ]
                                  }
                                }
                                """.formatted(title, teachingClassValue, gradeWeight)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createGradableStructuredAssignment(String token, Long offeringId, Long classId, int gradeWeight)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"结构化批改作业",
                                  "description":"客观题 + 简答题",
                                  "teachingClassId":%s,
                                  "openAt":"2026-04-01T08:00:00+08:00",
                                  "dueAt":"2026-04-30T23:59:59+08:00",
                                  "maxSubmissions":2,
                                  "gradeWeight":%s,
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
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.formatted(classId, gradeWeight)))
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

    private void submitObjectiveAssignment(String token, Long assignmentId, String selectedOptionKey) throws Exception {
        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long questionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["%s"]}
                                  ]
                                }
                                """.formatted(questionId, selectedOptionKey)))
                .andExpect(status().isCreated());
    }

    private GradeableSubmission submitGradableAssignment(String token, Long assignmentId) throws Exception {
        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        Long objectiveQuestionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");
        Long shortAnswerQuestionId = readLong(assignmentResult, "$.paper.sections[1].questions[0].id");

        MvcResult submissionResult = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A"]},
                                    {"assignmentQuestionId":%s,"answerText":"路径压缩会在查找时递归压缩父指针。"}
                                  ]
                                }
                                """.formatted(objectiveQuestionId, shortAnswerQuestionId)))
                .andExpect(status().isCreated())
                .andReturn();

        return new GradeableSubmission(
                readLong(submissionResult, "$.id"), readLong(submissionResult, "$.answers[1].id"));
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

    private record GradebookScenario(
            String teacherToken,
            String taAToken,
            String taBToken,
            Long offeringId,
            Long classAId,
            Long classBId,
            Long studentAUserId,
            Long studentBUserId,
            Long courseWideAssignmentId,
            Long classAssignmentId) {}

    private record GradeableSubmission(Long submissionId, Long shortAnswerId) {}
}
