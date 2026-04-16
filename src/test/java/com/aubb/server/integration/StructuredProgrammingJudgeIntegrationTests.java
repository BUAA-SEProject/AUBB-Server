package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class StructuredProgrammingJudgeIntegrationTests {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final DateTimeFormatter OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final FakeGoJudgeServer GO_JUDGE_SERVER = new FakeGoJudgeServer();
    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-structured-programming-test";

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
    void programmingAnswerRunsQuestionLevelJudgeAndSupportsAnswerScopedRequeue() throws Exception {
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
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.judgeCaseCount")
                        .value(2))
                .andReturn();
        Long questionId = readLong(assignmentResult, "$.paper.sections[0].questions[0].id");

        MvcResult submissionResult = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
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
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PENDING_PROGRAMMING_JUDGE"))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("main.py"))
                .andExpect(jsonPath("$.answers[0].files[2].path").value("helpers/math_utils.py"))
                .andExpect(jsonPath("$.scoreSummary.pendingProgrammingCount").value(1))
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long answerId = readLong(submissionResult, "$.answers[0].id");

        waitForLatestAnswerJudgeJobTerminal(answerId);

        mockMvc.perform(get("/api/v1/me/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$[0].submissionAnswerId").value(answerId))
                .andExpect(jsonPath("$[0].assignmentQuestionId").value(questionId))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].passedCaseCount").value(2))
                .andExpect(jsonPath("$[0].totalCaseCount").value(2))
                .andExpect(jsonPath("$[0].score").value(100))
                .andExpect(jsonPath("$[0].caseResults.length()").value(2))
                .andExpect(jsonPath("$[0].caseResults[0].score").value(60))
                .andExpect(jsonPath("$[0].caseResults[1].score").value(40));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGED"))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("main.py"))
                .andExpect(jsonPath("$.answers[0].files[2].path").value("helpers/math_utils.py"))
                .andExpect(jsonPath("$.answers[0].autoScore").value(100))
                .andExpect(jsonPath("$.answers[0].finalScore").value(100))
                .andExpect(jsonPath("$.scoreSummary.pendingProgrammingCount").value(0))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(100));

        assertThat(queryForString("SELECT grading_status FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo("PROGRAMMING_JUDGED");
        assertThat(queryForInt("SELECT auto_score FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo(100);
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo(100);

        JsonNode firstRequest = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(firstRequest.at("/cmd/0/copyIn/main.py/content").asText())
                .contains("from helpers.math_utils import add");
        assertThat(firstRequest
                        .at("/cmd/0/copyIn/helpers~1math_utils.py/content")
                        .asText())
                .contains("def add");

        mockMvc.perform(post("/api/v1/teacher/submission-answers/{answerId}/judge-jobs/requeue", answerId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionAnswerId").value(answerId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.triggerType").value("MANUAL_REJUDGE"));

        waitForAnswerJudgeJobCount(answerId, 2);
        waitForLatestAnswerJudgeJobTerminal(answerId);

        mockMvc.perform(get("/api/v1/teacher/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].triggerType").value("MANUAL_REJUDGE"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[1].triggerType").value("AUTO"));
    }

    @Test
    void programmingAnswerSupportsJava17AndCpp17() throws Exception {
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
        MvcResult javaSubmission = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", javaAssignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
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
                                  ]
                                }
                                """.formatted(javaQuestionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PENDING_PROGRAMMING_JUDGE"))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("Main.java"))
                .andReturn();

        Long javaSubmissionId = readLong(javaSubmission, "$.id");
        Long javaAnswerId = readLong(javaSubmission, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(javaAnswerId);

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", javaSubmissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGED"))
                .andExpect(jsonPath("$.answers[0].autoScore").value(100))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("Main.java"));
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", javaAnswerId))
                .isEqualTo(100);
        JsonNode javaRequest = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(javaRequest.at("/cmd/0/args/2").asText())
                .contains("/usr/bin/javac -encoding UTF-8 -d . Calculator.java Main.java && /usr/bin/java -cp . Main");
        assertThat(javaRequest.at("/cmd/0/copyIn/Calculator.java/content").asText())
                .contains("static int add");

        GO_JUDGE_SERVER.reset();
        MvcResult nestedJavaSubmission = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", javaAssignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
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
                                      "programmingLanguage":"JAVA17"
                                    }
                                  ]
                                }
                                """.formatted(javaQuestionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PENDING_PROGRAMMING_JUDGE"))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("solutions/Main.java"))
                .andReturn();

        Long nestedJavaSubmissionId = readLong(nestedJavaSubmission, "$.id");
        Long nestedJavaAnswerId = readLong(nestedJavaSubmission, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(nestedJavaAnswerId);

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", nestedJavaSubmissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGED"))
                .andExpect(jsonPath("$.answers[0].autoScore").value(100))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("solutions/Main.java"));
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", nestedJavaAnswerId))
                .isEqualTo(100);
        JsonNode nestedJavaRequest = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(nestedJavaRequest.at("/cmd/0/args/2").asText())
                .contains("/usr/bin/javac -encoding UTF-8 -d . solutions/Calculator.java solutions/Main.java"
                        + " && /usr/bin/java -cp . solutions.Main");
        assertThat(nestedJavaRequest
                        .at("/cmd/0/copyIn/solutions~1Calculator.java/content")
                        .asText())
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
        MvcResult cppSubmission = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", cppAssignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
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
                                  ]
                                }
                                """.formatted(cppQuestionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PENDING_PROGRAMMING_JUDGE"))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("main.cpp"))
                .andReturn();

        Long cppSubmissionId = readLong(cppSubmission, "$.id");
        Long cppAnswerId = readLong(cppSubmission, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(cppAnswerId);

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", cppSubmissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGED"))
                .andExpect(jsonPath("$.answers[0].autoScore").value(100))
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("main.cpp"));
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", cppAnswerId))
                .isEqualTo(100);
        JsonNode cppRequest = GO_JUDGE_SERVER.requests().getFirst();
        assertThat(cppRequest.at("/cmd/0/args/2").asText())
                .contains("/usr/bin/g++ -std=c++17 -O2 main.cpp -o main && ./main");
        assertThat(cppRequest.at("/cmd/0/copyIn/calc.h/content").asText()).contains("inline int add");
    }

    @Test
    void programmingAnswerRunsCustomJudgeScriptAndUsesReturnedCaseScores() throws Exception {
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

        MvcResult submissionResult = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
                                      "answerText":"a, b = map(int, input().split())\\nresult = a + b\\nif result > 10:\\n    result -= 1\\nprint(result)",
                                      "programmingLanguage":"PYTHON3"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PENDING_PROGRAMMING_JUDGE"))
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long answerId = readLong(submissionResult, "$.answers[0].id");

        waitForLatestAnswerJudgeJobTerminal(answerId);

        mockMvc.perform(get("/api/v1/me/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$[0].submissionAnswerId").value(answerId))
                .andExpect(jsonPath("$[0].assignmentQuestionId").value(questionId))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$[0].passedCaseCount").value(1))
                .andExpect(jsonPath("$[0].totalCaseCount").value(2))
                .andExpect(jsonPath("$[0].score").value(70))
                .andExpect(jsonPath("$[0].caseResults.length()").value(2))
                .andExpect(jsonPath("$[0].caseResults[0].score").value(60))
                .andExpect(jsonPath("$[0].caseResults[1].score").value(10))
                .andExpect(jsonPath("$[0].caseResults[1].errorMessage").value("命中部分分规则"));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGED"))
                .andExpect(jsonPath("$.answers[0].autoScore").value(70))
                .andExpect(jsonPath("$.answers[0].finalScore").value(70))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(70));

        assertThat(queryForString("SELECT grading_status FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo("PROGRAMMING_JUDGED");
        assertThat(queryForInt("SELECT auto_score FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo(70);
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo(70);
        assertThat(GO_JUDGE_SERVER.requests()).hasSize(4);

        JsonNode judgeRequest = GO_JUDGE_SERVER.requests().get(1);
        assertThat(judgeRequest
                        .at("/cmd/0/copyIn/_aubb_custom_judge.py/content")
                        .asText())
                .contains("#PARTIAL_SECOND_CASE");
        assertThat(judgeRequest
                        .at("/cmd/0/copyIn/_aubb_judge_context.json/content")
                        .asText())
                .contains("\"sampleRun\":false")
                .contains("\"maxScore\":60");
    }

    @Test
    void programmingAnswerTimeLimitExceededProducesStableJudgeSummary() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-TLE", "超时班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createStructuredProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        Long questionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

        MvcResult submissionResult = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
                                      "answerText":"#TIME_LIMIT\\nwhile True:\\n    pass",
                                      "programmingLanguage":"PYTHON3"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");
        Long answerId = readLong(submissionResult, "$.answers[0].id");

        waitForLatestAnswerJudgeJobTerminal(answerId);

        mockMvc.perform(get("/api/v1/me/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].verdict").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$[0].passedCaseCount").value(0))
                .andExpect(jsonPath("$[0].score").value(0))
                .andExpect(jsonPath("$[0].resultSummary").value(org.hamcrest.Matchers.containsString("超出时间限制")));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGED"))
                .andExpect(jsonPath("$.answers[0].autoScore").value(0))
                .andExpect(jsonPath("$.answers[0].finalScore").value(0))
                .andExpect(jsonPath("$.answers[0].feedbackText").value(org.hamcrest.Matchers.containsString("超出时间限制")));
    }

    private void waitForLatestAnswerJudgeJobTerminal(Long answerId) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (System.currentTimeMillis() < deadline) {
            String status = queryForString(
                    "SELECT status FROM judge_jobs WHERE submission_answer_id = ? ORDER BY id DESC LIMIT 1", answerId);
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("题目级评测任务未在预期时间内进入终态");
    }

    private void waitForAnswerJudgeJobCount(Long answerId, int expectedCount) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (System.currentTimeMillis() < deadline) {
            Integer count = queryForInt("SELECT COUNT(*) FROM judge_jobs WHERE submission_answer_id = ?", answerId);
            if (count != null && count == expectedCount) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("题目级评测任务数量未在预期时间内达到 " + expectedCount);
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
                #PARTIAL_SECOND_CASE
                import json
                import pathlib

                context = json.loads(pathlib.Path("_aubb_judge_context.json").read_text())
                actual = pathlib.Path("_aubb_actual_stdout.txt").read_text()
                expected = pathlib.Path("_aubb_expected_stdout.txt").read_text()
                if actual == expected:
                    print(json.dumps({"verdict": "ACCEPTED", "message": "脚本判定通过"}))
                elif context["stdinText"] == "7 8\\n" and actual == "14\\n":
                    print(json.dumps({"verdict": "WRONG_ANSWER", "score": 10, "message": "命中部分分规则"}))
                else:
                    print(json.dumps({"verdict": "WRONG_ANSWER", "message": "输出不匹配"}))
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
                                  "title":"结构化编程题作业",
                                  "description":"question-level judge",
                                  "teachingClassId":%s,
                                  "openAt":"%s",
                                  "dueAt":"%s",
                                  "maxSubmissions":3,
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
                                                {"stdinText":"2 3\\n","expectedStdout":"5\\n","score":60},
                                                {"stdinText":"7 8\\n","expectedStdout":"15\\n","score":40}
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
            if (server != null) {
                return;
            }
            try {
                server = HttpServer.create(new InetSocketAddress(0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException("无法启动测试 go-judge 服务器", exception);
            }
            server.createContext("/run", this::handleRun);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        void reset() {
            synchronized (requests) {
                requests.clear();
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<JsonNode> requests() {
            synchronized (requests) {
                return List.copyOf(requests);
            }
        }

        private void handleRun(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writePlain(exchange, 405, "method not allowed");
                return;
            }
            JsonNode request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            synchronized (requests) {
                requests.add(request);
            }

            String stdin = request.at("/cmd/0/files/0/content").asText();
            String pythonSource = readCopyInContent(request, "main.py");
            if (pythonSource.contains("#FAIL_HTTP")) {
                writePlain(exchange, 500, "judge unavailable");
                return;
            }

            if ("_aubb_custom_judge.py".equals(request.at("/cmd/0/args/1").asText())) {
                handleCustomJudge(exchange, request);
                return;
            }

            SimulatedExecution execution = simulateProgramExecution(request, stdin);
            byte[] response = OBJECT_MAPPER.writeValueAsBytes(List.of(Map.of(
                    "status",
                    execution.status(),
                    "exitStatus",
                    execution.exitStatus(),
                    "time",
                    1_000_000,
                    "memory",
                    65_536,
                    "runTime",
                    1_000_000,
                    "files",
                    Map.of("stdout", execution.stdout(), "stderr", execution.stderr()))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        private SimulatedExecution simulateProgramExecution(JsonNode request, String stdin) {
            String markerSource = readMarkerSource(request);
            if (markerSource.contains("#RUNTIME_ERROR")) {
                return new SimulatedExecution(
                        "Non Zero Exit Status", 1, "", "Traceback (most recent call last):\nboom\n");
            }
            if (markerSource.contains("#TIME_LIMIT")) {
                return new SimulatedExecution("Time Limit Exceeded", 1, "", "");
            }
            if (markerSource.contains("#MEMORY_LIMIT")) {
                return new SimulatedExecution("Memory Limit Exceeded", 1, "", "");
            }
            if (markerSource.contains("#OUTPUT_LIMIT")) {
                return new SimulatedExecution("Output Limit Exceeded", 1, "", "");
            }
            if (markerSource.contains("#COMPILE_ERROR")) {
                return new SimulatedExecution(
                        "Non Zero Exit Status", 1, "", "Compilation failed: simulated compiler error\n");
            }
            return new SimulatedExecution("Accepted", 0, simulateProgramStdout(request, stdin), "");
        }

        private String simulateProgramStdout(JsonNode request, String stdin) {
            String pythonSource = readCopyInContentBySuffix(request, "main.py");
            String pythonHelper = readCopyInContentBySuffix(request, "helper.py");
            if (pythonHelper.isEmpty()) {
                pythonHelper = readCopyInContentBySuffix(request, "helpers/math_utils.py");
            }
            if ((pythonSource.contains("from helper import add")
                            || pythonSource.contains("from helpers.math_utils import add"))
                    && pythonHelper.contains("def add")) {
                return addFromStdin(stdin, 0);
            }
            if (pythonSource.contains("result = a + b")) {
                String[] parts = stripTrailingNewline(stdin).split("\\s+");
                int result = Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]);
                if (result > 10) {
                    result -= 1;
                }
                return result + "\n";
            }

            String javaSource = readCopyInContentBySuffix(request, "Main.java");
            if (!javaSource.isEmpty()) {
                String helper = readCopyInContentBySuffix(request, "Calculator.java");
                if (javaSource.contains("Calculator.add(a, b)") && helper.contains("static int add")) {
                    return addFromStdin(stdin, 0);
                }
            }

            String cppSource = readCopyInContentBySuffix(request, "main.cpp");
            if (!cppSource.isEmpty()) {
                String helper = readCopyInContentBySuffix(request, "calc.h");
                if (cppSource.contains("#include \"calc.h\"") && helper.contains("inline int add")) {
                    return addFromStdin(stdin, 0);
                }
            }
            return "0\n";
        }

        private String readMarkerSource(JsonNode request) {
            for (String path : List.of("main.py", "Main.java", "main.cpp")) {
                String source = readCopyInContentBySuffix(request, path);
                if (!source.isEmpty()) {
                    return source;
                }
            }
            return "";
        }

        private String readCopyInContentBySuffix(JsonNode request, String suffix) {
            JsonNode copyIn = request.at("/cmd/0/copyIn");
            if (!copyIn.isObject()) {
                return "";
            }
            java.util.Iterator<String> fieldNames = copyIn.fieldNames();
            while (fieldNames.hasNext()) {
                String path = fieldNames.next();
                if (path.equals(suffix) || path.endsWith("/" + suffix)) {
                    return copyIn.path(path).path("content").asText();
                }
            }
            return "";
        }

        private String readCopyInContent(JsonNode request, String path) {
            return request.at("/cmd/0/copyIn/" + escapeJsonPointer(path) + "/content")
                    .asText();
        }

        private String addFromStdin(String stdin, int delta) {
            String[] parts = stripTrailingNewline(stdin).split("\\s+");
            return (Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]) + delta) + "\n";
        }

        private String escapeJsonPointer(String path) {
            return path.replace("~", "~0").replace("/", "~1");
        }

        private void handleCustomJudge(HttpExchange exchange, JsonNode request) throws IOException {
            String script =
                    request.at("/cmd/0/copyIn/_aubb_custom_judge.py/content").asText();
            JsonNode context = OBJECT_MAPPER.readTree(
                    request.at("/cmd/0/copyIn/_aubb_judge_context.json/content").asText());
            String actual =
                    request.at("/cmd/0/copyIn/_aubb_actual_stdout.txt/content").asText();
            String expected = request.at("/cmd/0/copyIn/_aubb_expected_stdout.txt/content")
                    .asText();

            String stdout;
            if (script.contains("#PARTIAL_SECOND_CASE")) {
                if (actual.equals(expected)) {
                    stdout = "{\"verdict\":\"ACCEPTED\",\"message\":\"脚本判定通过\"}";
                } else if ("7 8\n".equals(context.path("stdinText").asText()) && "14\n".equals(actual)) {
                    stdout = "{\"verdict\":\"WRONG_ANSWER\",\"score\":10,\"message\":\"命中部分分规则\"}";
                } else {
                    stdout = "{\"verdict\":\"WRONG_ANSWER\",\"message\":\"输出不匹配\"}";
                }
            } else {
                stdout = "{\"verdict\":\"WRONG_ANSWER\",\"message\":\"未知脚本\"}";
            }

            byte[] response = OBJECT_MAPPER.writeValueAsBytes(List.of(Map.of(
                    "status", "Accepted",
                    "exitStatus", 0,
                    "time", 1_000_000,
                    "memory", 65_536,
                    "runTime", 1_000_000,
                    "files", Map.of("stdout", stdout, "stderr", ""))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        private void writePlain(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        }

        private static String stripTrailingNewline(String value) {
            return value != null && value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
        }

        private record SimulatedExecution(String status, int exitStatus, String stdout, String stderr) {}
    }
}
