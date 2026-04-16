package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "aubb.judge.go-judge.enabled=true"})
@AutoConfigureMockMvc
@Import(com.aubb.server.TestcontainersConfiguration.class)
class ProgrammingWorkspaceIntegrationTests {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final DateTimeFormatter OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final FakeGoJudgeServer GO_JUDGE_SERVER = new FakeGoJudgeServer();
    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-programming-workspace-test";

    @Container
    static final GenericContainer<?> MINIO_CONTAINER = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    static {
        GO_JUDGE_SERVER.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("aubb.judge.go-judge.base-url", GO_JUDGE_SERVER::baseUrl);
        registry.add("aubb.judge.go-judge.enabled", () -> "true");
        registry.add("aubb.storage.minio.enabled", () -> "true");
        registry.add("aubb.storage.minio.auto-create-bucket", () -> "true");
        registry.add(
                "aubb.storage.minio.endpoint",
                () -> "http://" + MINIO_CONTAINER.getHost() + ":" + MINIO_CONTAINER.getMappedPort(9000));
        registry.add("aubb.storage.minio.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("aubb.storage.minio.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("aubb.storage.minio.bucket", () -> MINIO_BUCKET);
    }

    @AfterAll
    static void stopServer() {
        GO_JUDGE_SERVER.stop();
    }

    @BeforeEach
    void setUp() {
        GO_JUDGE_SERVER.reset();
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

        JsonNode request = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(request.at("/cmd/0/copyIn/main.py/content").asText()).contains("from helpers.math_utils import add");
        assertThat(request.at("/cmd/0/copyIn/helpers~1math_utils.py/content").asText())
                .contains("def add");
        assertThat(request.at("/cmd/0/files/0/content").asText()).isEqualTo("1 2\n");
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

        GO_JUDGE_SERVER.reset();
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
                                  "programmingLanguage":"JAVA17"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programmingLanguage").value("JAVA17"))
                .andExpect(jsonPath("$.entryFilePath").value("Main.java"))
                .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.files[1].path").value("Calculator.java"));

        JsonNode javaRequest = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(javaRequest.at("/cmd/0/args/2").asText()).contains("/usr/bin/javac Main.java && /usr/bin/java Main");
        assertThat(javaRequest.at("/cmd/0/copyIn/Main.java/content").asText()).contains("Calculator.add(a, b)");
        assertThat(javaRequest.at("/cmd/0/copyIn/Calculator.java/content").asText())
                .contains("static int add");

        Long cppAssignmentId = createCppProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, cppAssignmentId);
        Long cppQuestionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", cppAssignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

        GO_JUDGE_SERVER.reset();
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
                .andExpect(jsonPath("$.files[1].path").value("calc.h"));

        JsonNode cppRequest = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(cppRequest.at("/cmd/0/args/2").asText())
                .contains("/usr/bin/g++ -std=c++17 -O2 main.cpp -o main && ./main");
        assertThat(cppRequest.at("/cmd/0/copyIn/main.cpp/content").asText()).contains("#include \"calc.h\"");
        assertThat(cppRequest.at("/cmd/0/copyIn/calc.h/content").asText()).contains("inline int add");
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
        assertThat(GO_JUDGE_SERVER.requests()).hasSize(2);
        assertThat(GO_JUDGE_SERVER
                        .requests()
                        .get(1)
                        .at("/cmd/0/copyIn/_aubb_custom_judge.py/content")
                        .asText())
                .contains("#ALLOW_OFF_BY_ONE");
        assertThat(GO_JUDGE_SERVER
                        .requests()
                        .get(1)
                        .at("/cmd/0/copyIn/_aubb_judge_context.json/content")
                        .asText())
                .contains("\"sampleRun\":true");
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
                token, offeringId, classId, "[\"JAVA17\"]", "[\"java\"]", "STANDARD_IO", null);
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

    private static final class FakeGoJudgeServer {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private HttpServer server;
        private final List<JsonNode> requests = new ArrayList<>();

        void start() {
            try {
                server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/run", this::handleRun);
                server.setExecutor(Executors.newSingleThreadExecutor());
                server.start();
            } catch (IOException exception) {
                throw new IllegalStateException("无法启动 fake go-judge", exception);
            }
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        void reset() {
            requests.clear();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<JsonNode> requests() {
            return requests;
        }

        private void handleRun(HttpExchange exchange) throws IOException {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            JsonNode request = OBJECT_MAPPER.readTree(requestBytes);
            requests.add(request);

            if ("_aubb_custom_judge.py".equals(request.at("/cmd/0/args/1").asText())) {
                handleCustomJudge(exchange, request);
                return;
            }

            String stdin = request.at("/cmd/0/files/0/content").asText();
            String stdout = simulateProgramStdout(request, stdin);
            byte[] responseBytes = OBJECT_MAPPER.writeValueAsBytes(List.of(Map.of(
                    "status",
                    "Accepted",
                    "exitStatus",
                    0,
                    "time",
                    1_500_000L,
                    "memory",
                    4_096L,
                    "runTime",
                    1_700_000L,
                    "files",
                    Map.of("stdout", stdout, "stderr", ""))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }

        private String simulateProgramStdout(JsonNode request, String stdin) {
            String pythonSource = readCopyInContent(request, "main.py");
            if (!pythonSource.isEmpty()) {
                if (pythonSource.contains("print(a + b - 1)")) {
                    return addFromStdin(stdin, -1);
                }
                String helper = readCopyInContent(request, "helpers/math_utils.py");
                if (pythonSource.contains("from helpers.math_utils import add") && helper.contains("def add")) {
                    return addFromStdin(stdin, 0);
                }
            }

            String javaSource = readCopyInContent(request, "Main.java");
            if (!javaSource.isEmpty()) {
                String helper = readCopyInContent(request, "Calculator.java");
                if (javaSource.contains("Calculator.add(a, b)") && helper.contains("static int add")) {
                    return addFromStdin(stdin, 0);
                }
            }

            String cppSource = readCopyInContent(request, "main.cpp");
            if (!cppSource.isEmpty()) {
                String helper = readCopyInContent(request, "calc.h");
                if (cppSource.contains("#include \"calc.h\"") && helper.contains("inline int add")) {
                    return addFromStdin(stdin, 0);
                }
            }
            return "0\n";
        }

        private String readCopyInContent(JsonNode request, String path) {
            return request.at("/cmd/0/copyIn/" + escapeJsonPointer(path) + "/content")
                    .asText();
        }

        private String addFromStdin(String stdin, int delta) {
            String[] parts = stdin.strip().split("\\s+");
            return (Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]) + delta) + "\n";
        }

        private String escapeJsonPointer(String path) {
            return path.replace("~", "~0").replace("/", "~1");
        }

        private void handleCustomJudge(HttpExchange exchange, JsonNode request) throws IOException {
            String script =
                    request.at("/cmd/0/copyIn/_aubb_custom_judge.py/content").asText();
            String actual =
                    request.at("/cmd/0/copyIn/_aubb_actual_stdout.txt/content").asText();
            String expected = request.at("/cmd/0/copyIn/_aubb_expected_stdout.txt/content")
                    .asText();

            String stdout;
            if (script.contains("#ALLOW_OFF_BY_ONE")) {
                int actualValue = Integer.parseInt(actual.strip());
                int expectedValue = Integer.parseInt(expected.strip());
                if (Math.abs(actualValue - expectedValue) <= 1) {
                    stdout = "{\"verdict\":\"ACCEPTED\",\"message\":\"允许 1 的误差\"}";
                } else {
                    stdout = "{\"verdict\":\"WRONG_ANSWER\",\"message\":\"超出允许误差\"}";
                }
            } else {
                stdout = "{\"verdict\":\"WRONG_ANSWER\",\"message\":\"未知脚本\"}";
            }

            byte[] responseBytes = OBJECT_MAPPER.writeValueAsBytes(List.of(Map.of(
                    "status",
                    "Accepted",
                    "exitStatus",
                    0,
                    "time",
                    1_500_000L,
                    "memory",
                    4_096L,
                    "runTime",
                    1_700_000L,
                    "files",
                    Map.of("stdout", stdout, "stderr", ""))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        }
    }
}
