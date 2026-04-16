package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "aubb.judge.go-judge.enabled=true"})
@AutoConfigureMockMvc
@Import(com.aubb.server.TestcontainersConfiguration.class)
class ProgrammingWorkspaceIntegrationTests extends AbstractRealJudgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void stopServer() {
        // 真实 go-judge 与 MinIO 由 Testcontainers 生命周期管理。
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
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
        insertUser(2L, "student-a", "Student A", "student-a@example.com");

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
    void studentSavesProgrammingWorkspaceAndRunsSampleWithoutCreatingFormalSubmission() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-A", "A班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createStructuredProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.allowSampleRun")
                        .value(true))
                .andReturn();
        Long questionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");

        mockMvc.perform(put(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "entryFilePath":"main.py",
                                  "files":[
                                    {
                                      "path":"main.py",
                                      "content":"from helpers.math_utils import add\\na, b = map(int, input().split())\\nprint(add(a, b))"
                                    },
                                    {
                                      "path":"helpers/__init__.py",
                                      "content":""
                                    },
                                    {
                                      "path":"helpers/math_utils.py",
                                      "content":"def add(left, right):\\n    return left + right"
                                    }
                                  ],
                                  "programmingLanguage":"PYTHON3"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.assignmentQuestionId").value(questionId))
                .andExpect(jsonPath("$.programmingLanguage").value("PYTHON3"))
                .andExpect(jsonPath("$.entryFilePath").value("main.py"))
                .andExpect(jsonPath("$.files[0].path").value("main.py"))
                .andExpect(jsonPath("$.files[1].path").value("helpers/__init__.py"))
                .andExpect(jsonPath("$.files[2].path").value("helpers/math_utils.py"))
                .andExpect(jsonPath("$.codeText")
                        .value(org.hamcrest.Matchers.containsString("from helpers.math_utils import add")));

        mockMvc.perform(get(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/workspace",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programmingLanguage").value("PYTHON3"))
                .andExpect(jsonPath("$.entryFilePath").value("main.py"))
                .andExpect(jsonPath("$.files[2].path").value("helpers/math_utils.py"))
                .andExpect(jsonPath("$.codeText")
                        .value(org.hamcrest.Matchers.containsString("from helpers.math_utils import add")));

        MvcResult sampleRunResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "entryFilePath":"main.py",
                                          "files":[
                                            {
                                              "path":"main.py",
                                              "content":"from helpers.math_utils import add\\na, b = map(int, input().split())\\nprint(add(a, b))"
                                            },
                                            {
                                              "path":"helpers/__init__.py",
                                              "content":""
                                            },
                                            {
                                              "path":"helpers/math_utils.py",
                                              "content":"def add(left, right):\\n    return left + right"
                                            }
                                          ],
                                          "programmingLanguage":"PYTHON3"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.assignmentQuestionId").value(questionId))
                .andExpect(jsonPath("$.entryFilePath").value("main.py"))
                .andExpect(jsonPath("$.files[2].path").value("helpers/math_utils.py"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.stdoutText").value("3\n"))
                .andExpect(jsonPath("$.stderrText").isEmpty())
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("ACCEPTED")))
                .andReturn();

        Long sampleRunId = readLong(sampleRunResult, "$.id");

        mockMvc.perform(get(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sampleRunId))
                .andExpect(jsonPath("$[0].entryFilePath").value("main.py"))
                .andExpect(jsonPath("$[0].files[2].path").value("helpers/math_utils.py"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("ACCEPTED"));

        assertThat(queryForInt("SELECT COUNT(*) FROM submissions")).isZero();
        assertThat(queryForInt("SELECT COUNT(*) FROM judge_jobs")).isZero();
        assertThat(queryForInt("SELECT COUNT(*) FROM programming_workspaces")).isEqualTo(1);
        assertThat(queryForInt("SELECT COUNT(*) FROM programming_sample_runs")).isEqualTo(1);
        assertThat(queryForString("SELECT programming_language FROM programming_workspaces WHERE user_id = 4"))
                .isEqualTo("PYTHON3");
        assertThat(queryForString("SELECT entry_file_path FROM programming_workspaces WHERE user_id = 4"))
                .isEqualTo("main.py");
        assertThat(queryForString("SELECT source_files_json FROM programming_workspaces WHERE user_id = 4"))
                .contains("helpers/math_utils.py");
    }

    @Test
    void studentRunsJavaAndCppSampleRunsWithLanguageSpecificCommands() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-JC", "多语言班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long javaAssignmentId = createJavaProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, javaAssignmentId);
        Long javaQuestionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", javaAssignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

        mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                javaAssignmentId,
                                javaQuestionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "entryFilePath":"Main.java",
                                  "files":[
                                    {
                                      "path":"Main.java",
                                      "content":"import java.util.Scanner;\\npublic class Main {\\n  public static void main(String[] args) {\\n    Scanner scanner = new Scanner(System.in);\\n    int a = scanner.nextInt();\\n    int b = scanner.nextInt();\\n    System.out.println(Calculator.add(a, b));\\n  }\\n}"
                                    },
                                    {
                                      "path":"Calculator.java",
                                      "content":"final class Calculator {\\n  private Calculator() {}\\n  static int add(int left, int right) {\\n    return left + right;\\n  }\\n}"
                                    }
                                  ],
                                  "programmingLanguage":"JAVA21"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programmingLanguage").value("JAVA21"))
                .andExpect(jsonPath("$.entryFilePath").value("Main.java"))
                .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.stdoutText").value("3\n"))
                .andExpect(jsonPath("$.files[1].path").value("Calculator.java"));

        mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                javaAssignmentId,
                                javaQuestionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "entryFilePath":"solutions/Main.java",
                                  "files":[
                                    {
                                      "path":"solutions/Main.java",
                                      "content":"package solutions;\\nimport java.util.Scanner;\\npublic class Main {\\n  public static void main(String[] args) {\\n    Scanner scanner = new Scanner(System.in);\\n    int a = scanner.nextInt();\\n    int b = scanner.nextInt();\\n    System.out.println(Calculator.add(a, b));\\n  }\\n}"
                                    },
                                    {
                                      "path":"solutions/Calculator.java",
                                      "content":"package solutions;\\nfinal class Calculator {\\n  private Calculator() {}\\n  static int add(int left, int right) {\\n    return left + right;\\n  }\\n}"
                                    }
                                  ],
                                  "programmingLanguage":"JAVA21"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programmingLanguage").value("JAVA21"))
                .andExpect(jsonPath("$.entryFilePath").value("solutions/Main.java"))
                .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.stdoutText").value("3\n"))
                .andExpect(jsonPath("$.files[1].path").value("solutions/Calculator.java"));

        Long cppAssignmentId = createCppProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, cppAssignmentId);
        Long cppQuestionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", cppAssignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

        mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                cppAssignmentId,
                                cppQuestionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "entryFilePath":"main.cpp",
                                  "files":[
                                    {
                                      "path":"main.cpp",
                                      "content":"#include <iostream>\\n#include \\\"calc.h\\\"\\nint main() {\\n  int a, b;\\n  std::cin >> a >> b;\\n  std::cout << add(a, b) << '\\\\n';\\n  return 0;\\n}"
                                    },
                                    {
                                      "path":"calc.h",
                                      "content":"inline int add(int left, int right) {\\n  return left + right;\\n}"
                                    }
                                  ],
                                  "programmingLanguage":"CPP17"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programmingLanguage").value("CPP17"))
                .andExpect(jsonPath("$.entryFilePath").value("main.cpp"))
                .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.stdoutText").value("3\n"))
                .andExpect(jsonPath("$.files[1].path").value("calc.h"));
    }

    @Test
    void studentSeesCompileFailureSummaryForJavaSampleRun() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-JFAIL", "编译失败班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createJavaProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);
        Long questionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

        mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "entryFilePath":"Main.java",
                                  "files":[
                                    {
                                      "path":"Main.java",
                                      "content":"// #COMPILE_ERROR\\npublic class Main {\\n  public static void main(String[] args) {\\n    System.out.println(1)\\n  }\\n}"
                                    }
                                  ],
                                  "programmingLanguage":"JAVA21"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.verdict").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.stderrText").value(org.hamcrest.Matchers.containsString("error:")))
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("编译失败")));
    }

    @Test
    void studentRunsCustomJudgeSampleRunAndReceivesCustomVerdict() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-B", "B班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createCustomScriptProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.judgeMode")
                        .value("CUSTOM_SCRIPT"))
                .andReturn();
        Long questionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");

        MvcResult sampleRunResult = mockMvc.perform(post(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                        {
                                          "codeText":"a, b = map(int, input().split())\\nprint(a + b - 1)",
                                          "programmingLanguage":"PYTHON3"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.assignmentQuestionId").value(questionId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.stdoutText").value("2\n"))
                .andExpect(jsonPath("$.stderrText").isEmpty())
                .andExpect(jsonPath("$.resultSummary").value(org.hamcrest.Matchers.containsString("允许 1 的误差")))
                .andReturn();

        Long sampleRunId = readLong(sampleRunResult, "$.id");

        mockMvc.perform(get(
                                "/api/v1/me/assignments/{assignmentId}/programming-questions/{questionId}/sample-runs",
                                assignmentId,
                                questionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sampleRunId))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].stdoutText").value("2\n"));

        assertThat(queryForInt("SELECT COUNT(*) FROM submissions")).isZero();
        assertThat(queryForInt("SELECT COUNT(*) FROM judge_jobs")).isZero();
        assertThat(queryForInt("SELECT COUNT(*) FROM programming_sample_runs")).isEqualTo(1);
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
                .andReturn();
        return readLong(result, "$.id");
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
                                  "gradeLevel":"2026",
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

    private Long createStructuredProgrammingAssignment(String token, Long offeringId, Long classId) throws Exception {
        return createProgrammingAssignment(
                token, offeringId, classId, "[\"PYTHON3\"]", "[\"py\"]", "STANDARD_IO", null);
    }

    private Long createJavaProgrammingAssignment(String token, Long offeringId, Long classId) throws Exception {
        return createProgrammingAssignment(
                token, offeringId, classId, "[\"JAVA21\"]", "[\"java\"]", "STANDARD_IO", null);
    }

    private Long createCppProgrammingAssignment(String token, Long offeringId, Long classId) throws Exception {
        return createProgrammingAssignment(
                token, offeringId, classId, "[\"CPP17\"]", "[\"cpp\",\"h\"]", "STANDARD_IO", null);
    }

    private Long createCustomScriptProgrammingAssignment(String token, Long offeringId, Long classId) throws Exception {
        return createProgrammingAssignment(
                token, offeringId, classId, "[\"PYTHON3\"]", "[\"py\"]", "CUSTOM_SCRIPT", """
                #ALLOW_OFF_BY_ONE
                import json
                import pathlib

                actual = pathlib.Path("_aubb_actual_stdout.txt").read_text()
                expected = pathlib.Path("_aubb_expected_stdout.txt").read_text()
                actual_value = int(actual.strip())
                expected_value = int(expected.strip())
                if abs(actual_value - expected_value) <= 1:
                    print(json.dumps({"verdict": "ACCEPTED", "message": "允许 1 的误差"}))
                else:
                    print(json.dumps({"verdict": "WRONG_ANSWER", "message": "超出允许误差"}))
                """);
    }

    private Long createProgrammingAssignment(
            String token,
            Long offeringId,
            Long classId,
            String supportedLanguagesJson,
            String acceptedExtensionsJson,
            String judgeMode,
            String customJudgeScript)
            throws Exception {
        String customJudgeScriptJson = customJudgeScript == null
                ? "null"
                : tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(customJudgeScript);
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"编程题工作区作业",
                                  "description":"workspace sample run",
                                  "teachingClassId":%s,
                                  "openAt":"%s",
                                  "dueAt":"%s",
                                  "maxSubmissions":1,
                                  "paper":{
                                    "sections":[
                                      {
                                        "title":"编程题",
                                        "questions":[
                                          {
                                            "title":"A+B",
                                            "prompt":"读取两个整数并输出和。",
                                            "questionType":"PROGRAMMING",
                                            "score":100,
                                            "config":{
                                              "supportedLanguages":%s,
                                              "acceptedExtensions":%s,
                                              "allowMultipleFiles":true,
                                              "allowSampleRun":true,
                                              "sampleStdinText":"1 2\\n",
                                              "sampleExpectedStdout":"3\\n",
                                              "timeLimitMs":1000,
                                              "memoryLimitMb":128,
                                              "outputLimitKb":64,
                                              "judgeMode":"%s",
                                              "customJudgeScript":%s,
                                              "judgeCases":[
                                                {"stdinText":"2 3\\n","expectedStdout":"5\\n","score":100}
                                              ]
                                            }
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.formatted(
                                        classId,
                                        OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.ofHours(8))
                                                .minusDays(1)),
                                        OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.ofHours(8))
                                                .plusDays(3)),
                                        supportedLanguagesJson,
                                        acceptedExtensionsJson,
                                        judgeMode,
                                        customJudgeScriptJson)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
    }

    private void publishAssignment(String token, Long assignmentId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/publish", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
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

    private Integer queryForInt(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String queryForString(String sql, Object... args) {
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null, args);
    }
}
