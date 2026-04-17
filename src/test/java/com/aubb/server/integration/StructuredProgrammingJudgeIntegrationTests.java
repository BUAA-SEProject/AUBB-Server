package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.common.storage.ObjectStorageService;
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
class StructuredProgrammingJudgeIntegrationTests extends AbstractRealJudgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectStorageService objectStorageService;

    @AfterAll
    static void stopServer() {
        // 真实 go-judge 与 MinIO 由 Testcontainers 生命周期管理。
    }

    @BeforeEach
    void setUp() {
        resetJudgeTables(jdbcTemplate, """
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

        IntegrationTestAwait.awaitCount(() -> queryForInt("""
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 4
                                  AND n.type = 'JUDGE_COMPLETED'
                                """), 1);

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
    void submissionScopedRequeueSupportsStructuredProgrammingAnswers() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-RQ", "重排队班", 2026);
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
                                      "entryFilePath":"main.py",
                                      "files":[
                                        {
                                          "path":"main.py",
                                          "content":"a, b = map(int, input().split())\\nprint(a + b)"
                                        }
                                      ],
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

        mockMvc.perform(post("/api/v1/teacher/submissions/{submissionId}/judge-jobs/requeue", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionId").value(submissionId))
                .andExpect(jsonPath("$.submissionAnswerId").value(answerId))
                .andExpect(jsonPath("$.assignmentQuestionId").value(questionId))
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
    void programmingAnswerSupportsJava21AndCpp17() throws Exception {
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
                                      "programmingLanguage":"JAVA21"
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
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("Main.java"))
                .andExpect(jsonPath("$.answers[0].feedbackText").value(org.hamcrest.Matchers.containsString("2/2")));
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", javaAnswerId))
                .isEqualTo(100);

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
                                      "programmingLanguage":"JAVA21"
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
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("solutions/Main.java"))
                .andExpect(jsonPath("$.answers[0].feedbackText").value(org.hamcrest.Matchers.containsString("2/2")));
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", nestedJavaAnswerId))
                .isEqualTo(100);

        Long cppAssignmentId = createCppProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, cppAssignmentId);
        Long cppQuestionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", cppAssignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

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
                .andExpect(jsonPath("$.answers[0].entryFilePath").value("main.cpp"))
                .andExpect(jsonPath("$.answers[0].feedbackText").value(org.hamcrest.Matchers.containsString("2/2")));
        assertThat(queryForInt("SELECT final_score FROM submission_answers WHERE id = ?", cppAnswerId))
                .isEqualTo(100);
    }

    @Test
    void programmingAnswerSupportsCompileAndRunArgsAndExposesDetailedReport() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-ARGS", "参数班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createCppArgsProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.compileArgs[0]")
                        .value("-DANSWER=41"))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.runArgs[0]")
                        .value("1"))
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
                                      "entryFilePath":"src/main.cpp",
                                      "files":[
                                        {
                                          "path":"src/main.cpp",
                                          "content":"#include <cstdlib>\\n#include <iostream>\\n#include \\\"math_utils.h\\\"\\nint main(int argc, char** argv) {\\n  std::cout << add(ANSWER, std::atoi(argv[1])) << \\\"\\\\n\\\";\\n}\\n"
                                        },
                                        {
                                          "path":"src/math_utils.cpp",
                                          "content":"#include \\\"math_utils.h\\\"\\nint add(int left, int right) { return left + right; }\\n"
                                        },
                                        {
                                          "path":"src/math_utils.h",
                                          "content":"int add(int left, int right);\\n"
                                        }
                                      ],
                                      "programmingLanguage":"CPP17"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long answerId = readLong(submissionResult, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(answerId);

        MvcResult jobsResult = mockMvc.perform(get("/api/v1/me/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(100))
                .andExpect(jsonPath("$[0].detailReportAvailable").value(true))
                .andExpect(jsonPath("$[0].artifactTraceAvailable").value(true))
                .andReturn();
        Long judgeJobId = readLong(jobsResult, "$[0].id");

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactTrace.storageMode").value("OBJECT_STORAGE"))
                .andExpect(jsonPath("$.artifactTrace.detailReport.storedInObjectStorage")
                        .value(true))
                .andExpect(jsonPath("$.artifactTrace.sourceSnapshot.storedInObjectStorage")
                        .value(true))
                .andExpect(jsonPath("$.artifactTrace.artifactManifest.storedInObjectStorage")
                        .value(true))
                .andExpect(jsonPath("$.executionMetadata.programmingLanguage").value("CPP17"))
                .andExpect(jsonPath("$.executionMetadata.compileArgs[0]").value("-DANSWER=41"))
                .andExpect(jsonPath("$.executionMetadata.runArgs[0]").value("1"))
                .andExpect(jsonPath("$.caseReports[0].stdoutText").value("42\n"))
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").doesNotExist());

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").value("42\n"))
                .andExpect(jsonPath("$.caseReports[0].compileCommand").isArray())
                .andExpect(jsonPath("$.caseReports[0].compileCommand", org.hamcrest.Matchers.hasItem("-DANSWER=41")))
                .andExpect(jsonPath("$.caseReports[0].runCommand[1]").value("1"));

        MvcResult studentDownload = mockMvc.perform(
                        get("/api/v1/me/judge-jobs/{judgeJobId}/report/download", judgeJobId)
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(studentDownload.getResponse().getHeader("Content-Disposition"))
                .contains("judge-job-%s-report.json".formatted(judgeJobId));
        assertThat(studentDownload.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"artifactTrace\"")
                .doesNotContain("\"expectedStdout\":\"42\\n\"");

        MvcResult teacherDownload = mockMvc.perform(
                        get("/api/v1/teacher/judge-jobs/{judgeJobId}/report/download", judgeJobId)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(teacherDownload.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"expectedStdout\" : \"42\\n\"")
                .contains("\"artifactTrace\"");

        String detailReportObjectKey =
                queryForString("SELECT detail_report_object_key FROM judge_jobs WHERE id = ?", judgeJobId);
        String sourceSnapshotObjectKey =
                queryForString("SELECT source_snapshot_object_key FROM judge_jobs WHERE id = ?", judgeJobId);
        String artifactManifestObjectKey =
                queryForString("SELECT artifact_manifest_object_key FROM judge_jobs WHERE id = ?", judgeJobId);
        String artifactTraceJson =
                queryForString("SELECT artifact_trace_json FROM judge_jobs WHERE id = ?", judgeJobId);
        assertThat(detailReportObjectKey).isNotBlank();
        assertThat(sourceSnapshotObjectKey).isNotBlank();
        assertThat(artifactManifestObjectKey).isNotBlank();
        assertThat(queryForString("SELECT detail_report_json FROM judge_jobs WHERE id = ?", judgeJobId))
                .isNull();
        assertThat(artifactTraceJson)
                .contains("\"judgeJobId\":%s".formatted(judgeJobId))
                .contains("\"submissionId\"")
                .contains("\"submissionAnswerId\":%s".formatted(answerId))
                .contains("\"artifactManifest\"");
        assertThat(new String(
                        objectStorageService.getObject(detailReportObjectKey).content(), StandardCharsets.UTF_8))
                .contains("\"compileArgs\":[\"-DANSWER=41\"]")
                .contains("\"runArgs\":[\"1\"]")
                .contains("\"submissionAnswerId\":%s".formatted(answerId))
                .contains("\"mode\":\"SUBMISSION_ANSWER\"");
        assertThat(new String(
                        objectStorageService.getObject(sourceSnapshotObjectKey).content(), StandardCharsets.UTF_8))
                .contains("\"entryFilePath\":\"src/main.cpp\"")
                .contains("\"submissionAnswerId\":%s".formatted(answerId))
                .contains("\"path\":\"src/math_utils.cpp\"");
        assertThat(new String(
                        objectStorageService
                                .getObject(artifactManifestObjectKey)
                                .content(),
                        StandardCharsets.UTF_8))
                .contains("\"storageMode\":\"OBJECT_STORAGE\"")
                .contains(
                        "\"includedArtifacts\":[\"DETAIL_REPORT\",\"CASE_OUTPUTS\",\"RUN_LOGS\",\"SOURCE_SNAPSHOT_OR_REF\"]");
    }

    @Test
    void judgeReportDownloadFallsBackToLegacyInlineJsonForHistoricalJobs() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-LEGACY", "历史兼容班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createStructuredProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
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
                                          "content":"a, b = map(int, input().split())\\nprint(a + b)"
                                        }
                                      ],
                                      "programmingLanguage":"PYTHON3"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long answerId = readLong(submissionResult, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(answerId);
        Long judgeJobId = jdbcTemplate.queryForObject(
                "SELECT id FROM judge_jobs WHERE submission_answer_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                answerId);

        String detailReportObjectKey =
                queryForString("SELECT detail_report_object_key FROM judge_jobs WHERE id = ?", judgeJobId);
        String detailReportJson =
                new String(objectStorageService.getObject(detailReportObjectKey).content(), StandardCharsets.UTF_8);
        jdbcTemplate.update("""
                UPDATE judge_jobs
                SET detail_report_json = ?,
                    detail_report_object_key = NULL,
                    source_snapshot_object_key = NULL,
                    artifact_manifest_object_key = NULL,
                    artifact_trace_json = NULL
                WHERE id = ?
                """, detailReportJson, judgeJobId);

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactTrace").doesNotExist())
                .andExpect(jsonPath("$.caseReports[0].stdoutText").value("5\n"));

        MvcResult teacherDownload = mockMvc.perform(
                        get("/api/v1/teacher/judge-jobs/{judgeJobId}/report/download", judgeJobId)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(teacherDownload.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"stdoutText\"")
                .contains("\"expectedStdout\" : \"5\\n\"");
    }

    @Test
    void programmingAnswerSupportsGoAndExecutionEnvironmentOverrides() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-GO", "Go 班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createGoEnvironmentProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.supportedLanguages[0]")
                        .value("GO122"))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.executionEnvironment.profileName")
                        .value("GO_ENV_V1"))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.executionEnvironment.languageVersion")
                        .value("go1.22"))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.executionEnvironment.workingDirectory")
                        .value("cmd/aubb"))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.executionEnvironment.cpuRateLimit")
                        .value(1000))
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
                                      "entryFilePath":"cmd/aubb/main.go",
                                      "files":[
                                        {
                                          "path":"go.mod",
                                          "content":"module aubbtest\\n\\ngo 1.22\\n"
                                        },
                                        {
                                          "path":"cmd/aubb/main.go",
                                          "content":"package main\\n\\nimport (\\n  \\\"fmt\\\"\\n  \\\"os\\\"\\n  \\\"strconv\\\"\\n\\n  \\\"aubbtest/internal/add\\\"\\n)\\n\\nfunc main() {\\n  left, _ := strconv.Atoi(os.Getenv(\\\"AUBB_OFFSET\\\"))\\n  right, _ := strconv.Atoi(os.Getenv(\\\"AUBB_EXTRA\\\"))\\n  fmt.Println(add.Sum(left, right))\\n}\\n"
                                        },
                                        {
                                          "path":"internal/add/add.go",
                                          "content":"package add\\n\\nfunc Sum(left, right int) int {\\n  return left + right\\n}\\n"
                                        }
                                      ],
                                      "programmingLanguage":"GO122"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PENDING_PROGRAMMING_JUDGE"))
                .andReturn();

        Long answerId = readLong(submissionResult, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(answerId);

        MvcResult jobsResult = mockMvc.perform(get("/api/v1/me/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(100))
                .andExpect(jsonPath("$[0].detailReportAvailable").value(true))
                .andReturn();
        Long judgeJobId = readLong(jobsResult, "$[0].id");

        mockMvc.perform(get("/api/v1/me/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMetadata.programmingLanguage").value("GO122"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.profileName")
                        .value("GO_ENV_V1"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.languageVersion")
                        .value("go1.22"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.workingDirectory")
                        .value("cmd/aubb"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.cpuRateLimit")
                        .value(1000))
                .andExpect(jsonPath("$.caseReports[0].stdoutText").value("42\n"))
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").doesNotExist());

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseReports[0].expectedStdout").value("42\n"))
                .andExpect(jsonPath("$.caseReports[0].runCommand[0]").value("./main"));
    }

    @Test
    void programmingAnswerUsesLanguageSpecificEnvironmentProfiles() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-MULTI", "多语言环境班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long javaProfileId = createJudgeEnvironmentProfile(teacherToken, offeringId, """
                {
                  "profileCode":"JAVA_PKG_V1",
                  "profileName":"Java 包运行模板",
                  "description":"多文件 package 工程",
                  "programmingLanguage":"JAVA21",
                  "environment":{
                    "workingDirectory":"solutions",
                    "compileCommand":"/opt/java/openjdk/bin/javac -encoding UTF-8 -d . ${JAVA_SOURCE_FILES}",
                    "runCommand":"/opt/java/openjdk/bin/java -cp . ${JAVA_LAUNCH_CLASS}",
                    "cpuRateLimit":1000
                  }
                }
                """);
        Long goProfileId = createJudgeEnvironmentProfile(teacherToken, offeringId, """
                {
                  "profileCode":"GO_ENV_V2",
                  "profileName":"Go 多文件模板",
                  "description":"go build 工作目录",
                  "programmingLanguage":"GO122",
                  "environment":{
                    "languageVersion":"go1.22",
                    "workingDirectory":"cmd/aubb",
                    "compileCommand":"/usr/local/go/bin/go build -o main .",
                    "runCommand":"./main",
                    "cpuRateLimit":1000,
                    "environmentVariables":{
                      "GOCACHE":"/tmp/go-build",
                      "CGO_ENABLED":"0"
                    }
                  }
                }
                """);

        Long assignmentId = createLanguageProfileProgrammingAssignment(
                teacherToken, offeringId, classId, javaProfileId, goProfileId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult assignmentResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sections[0].questions[0].config.languageExecutionEnvironments.length()")
                        .value(2))
                .andExpect(jsonPath(
                                "$.paper.sections[0].questions[0].config.languageExecutionEnvironments[0].programmingLanguage")
                        .value("JAVA21"))
                .andExpect(jsonPath(
                                "$.paper.sections[0].questions[0].config.languageExecutionEnvironments[0].executionEnvironment.profileId")
                        .value(javaProfileId))
                .andExpect(jsonPath(
                                "$.paper.sections[0].questions[0].config.languageExecutionEnvironments[1].programmingLanguage")
                        .value("GO122"))
                .andExpect(jsonPath(
                                "$.paper.sections[0].questions[0].config.languageExecutionEnvironments[1].executionEnvironment.profileId")
                        .value(goProfileId))
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
                                      "entryFilePath":"solutions/com/example/Main.java",
                                      "files":[
                                        {
                                          "path":"solutions/com/example/Main.java",
                                          "content":"package com.example;\\n\\npublic class Main {\\n  public static void main(String[] args) {\\n    System.out.println(Adder.sum(20, 22));\\n  }\\n}\\n"
                                        },
                                        {
                                          "path":"solutions/com/example/Adder.java",
                                          "content":"package com.example;\\n\\npublic final class Adder {\\n  private Adder() {}\\n\\n  public static int sum(int left, int right) {\\n    return left + right;\\n  }\\n}\\n"
                                        }
                                      ],
                                      "programmingLanguage":"JAVA21"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated())
                .andReturn();

        Long answerId = readLong(submissionResult, "$.answers[0].id");
        waitForLatestAnswerJudgeJobTerminal(answerId);

        MvcResult jobsResult = mockMvc.perform(get("/api/v1/me/submission-answers/{answerId}/judge-jobs", answerId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(100))
                .andReturn();
        Long judgeJobId = readLong(jobsResult, "$[0].id");

        mockMvc.perform(get("/api/v1/teacher/judge-jobs/{judgeJobId}/report", judgeJobId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMetadata.programmingLanguage").value("JAVA21"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.profileId")
                        .value(javaProfileId))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.profileCode")
                        .value("JAVA_PKG_V1"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.profileScope")
                        .value("OFFERING"))
                .andExpect(jsonPath("$.executionMetadata.executionEnvironment.workingDirectory")
                        .value("solutions"))
                .andExpect(jsonPath("$.caseReports[0].stdoutText").value("42\n"));
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
    }

    @Test
    void brokenCustomJudgeScriptMarksJobFailedAndPreservesPendingAnswerState() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-BROKEN", "损坏脚本班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createBrokenCustomScriptProgrammingAssignment(teacherToken, offeringId, classId);
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
                                      "answerText":"a, b = map(int, input().split())\\nprint(a + b)",
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
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].verdict").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$[0].errorMessage").value(org.hamcrest.Matchers.containsString("JSON 无法解析")))
                .andExpect(jsonPath("$[0].resultSummary").value("SYSTEM_ERROR，评测执行失败"));

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("PROGRAMMING_JUDGE_FAILED"))
                .andExpect(jsonPath("$.answers[0].autoScore").doesNotExist())
                .andExpect(jsonPath("$.answers[0].finalScore").doesNotExist())
                .andExpect(jsonPath("$.answers[0].feedbackText").value(org.hamcrest.Matchers.containsString("评测失败")));

        assertThat(queryForString("SELECT grading_status FROM submission_answers WHERE id = ?", answerId))
                .isEqualTo("PROGRAMMING_JUDGE_FAILED");
    }

    @Test
    void judgeCleanupDrainsAsyncWorkBeforeTruncate() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-RESET", "清理班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long assignmentId = createStructuredProgrammingAssignment(teacherToken, offeringId, classId);
        publishAssignment(teacherToken, assignmentId);

        Long questionId = readLong(
                mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.paper.sections[0].questions[0].id");

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "answers":[
                                    {
                                      "assignmentQuestionId":%s,
                                      "answerText":"a, b = map(int, input().split())\\nprint(a + b)",
                                      "programmingLanguage":"PYTHON3"
                                    }
                                  ]
                                }
                                """.formatted(questionId)))
                .andExpect(status().isCreated());

        resetJudgeTables(jdbcTemplate, """
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

        assertThat(queryForInt("SELECT COUNT(*) FROM judge_jobs")).isZero();
        assertThat(queryForInt("SELECT COUNT(*) FROM submission_answers")).isZero();
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
        String latestStatus = null;
        String answerStatus = null;
        String errorMessage = null;
        String resultSummary = null;
        while (System.currentTimeMillis() < deadline) {
            latestStatus = queryForString(
                    "SELECT status FROM judge_jobs WHERE submission_answer_id = ? ORDER BY id DESC LIMIT 1", answerId);
            answerStatus = queryForString("SELECT grading_status FROM submission_answers WHERE id = ?", answerId);
            errorMessage = queryForString(
                    "SELECT error_message FROM judge_jobs WHERE submission_answer_id = ? ORDER BY id DESC LIMIT 1",
                    answerId);
            resultSummary = queryForString(
                    "SELECT result_summary FROM judge_jobs WHERE submission_answer_id = ? ORDER BY id DESC LIMIT 1",
                    answerId);
            if (("SUCCEEDED".equals(latestStatus) || "FAILED".equals(latestStatus))
                    && ("PROGRAMMING_JUDGED".equals(answerStatus) || "PROGRAMMING_JUDGE_FAILED".equals(answerStatus))) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("题目级评测任务未在预期时间内进入终态，jobStatus=%s, answerStatus=%s, error=%s, summary=%s"
                .formatted(latestStatus, answerStatus, errorMessage, resultSummary));
    }

    private void waitForAnswerJudgeJobCount(Long answerId, int expectedCount) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        Integer latestCount = null;
        while (System.currentTimeMillis() < deadline) {
            latestCount = queryForInt("SELECT COUNT(*) FROM judge_jobs WHERE submission_answer_id = ?", answerId);
            if (latestCount != null && latestCount == expectedCount) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("题目级评测任务数量未在预期时间内达到 %s，current=%s".formatted(expectedCount, latestCount));
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
                token, offeringId, classId, "[\"JAVA21\"]", "[\"java\"]", "STANDARD_IO", null);
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

    private Long createBrokenCustomScriptProgrammingAssignment(String token, Long offeringId, Long classId)
            throws Exception {
        return createProgrammingAssignment(
                token, offeringId, classId, "[\"PYTHON3\"]", "[\"py\"]", "CUSTOM_SCRIPT", """
                print("not-json")
                """);
    }

    private Long createCppArgsProgrammingAssignment(String token, Long offeringId, Long classId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"编译参数编程题作业",
                                  "description":"compile and run args",
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
                                            "title":"参数化 A+B",
                                            "prompt":"编译参数与运行参数共同决定输出。",
                                            "questionType":"PROGRAMMING",
                                            "score":100,
                                            "config":{
                                              "supportedLanguages":["CPP17"],
                                              "acceptedExtensions":["cpp","h"],
                                              "allowMultipleFiles":true,
                                              "allowSampleRun":true,
                                              "sampleStdinText":"0\\n",
                                              "sampleExpectedStdout":"42\\n",
                                              "timeLimitMs":1000,
                                              "memoryLimitMb":128,
                                              "outputLimitKb":64,
                                              "compileArgs":["-DANSWER=41"],
                                              "runArgs":["1"],
                                              "judgeMode":"STANDARD_IO",
                                              "judgeCases":[
                                                {"stdinText":"0\\n","expectedStdout":"42\\n","score":100}
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
                                                .plusDays(3)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createGoEnvironmentProgrammingAssignment(String token, Long offeringId, Long classId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"Go 运行环境编程题作业",
                                  "description":"go runtime environment",
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
                                            "title":"Go 环境 A+B",
                                            "prompt":"验证 Go 语言与运行环境覆盖。",
                                            "questionType":"PROGRAMMING",
                                            "score":100,
                                            "config":{
                                              "supportedLanguages":["GO122"],
                                              "acceptedExtensions":["go","mod"],
                                              "allowMultipleFiles":true,
                                              "allowSampleRun":true,
                                              "sampleStdinText":"0\\n",
                                              "sampleExpectedStdout":"42\\n",
                                              "timeLimitMs":1500,
                                              "memoryLimitMb":128,
                                              "outputLimitKb":64,
                                              "judgeMode":"STANDARD_IO",
                                              "executionEnvironment":{
                                                "profileName":"GO_ENV_V1",
                                                "languageVersion":"go1.22",
                                                "workingDirectory":"cmd/aubb",
                                                "initScript":". ./bootstrap.sh",
                                                "compileCommand":"/usr/local/go/bin/go build -o main .",
                                                "runCommand":"./main",
                                                "cpuRateLimit":1000,
                                                "environmentVariables":{
                                                  "GOCACHE":"/tmp/go-build",
                                                  "CGO_ENABLED":"0",
                                                  "AUBB_EXTRA":"2"
                                                },
                                                "supportFiles":[
                                                  {
                                                    "path":"cmd/aubb/bootstrap.sh",
                                                    "content":"export AUBB_OFFSET=40\\n"
                                                  }
                                                ]
                                              },
                                              "judgeCases":[
                                                {"stdinText":"0\\n","expectedStdout":"42\\n","score":100}
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
                                                .plusDays(3)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createJudgeEnvironmentProfile(String token, Long offeringId, String body) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/teacher/course-offerings/{offeringId}/judge-environment-profiles", offeringId)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createLanguageProfileProgrammingAssignment(
            String token, Long offeringId, Long classId, Long javaProfileId, Long goProfileId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"多语言环境模板作业",
                                  "description":"按语言选择环境模板",
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
                                            "title":"多语言 A+B",
                                            "prompt":"支持 Java 和 Go 的运行环境模板。",
                                            "questionType":"PROGRAMMING",
                                            "score":100,
                                            "config":{
                                              "supportedLanguages":["JAVA21","GO122"],
                                              "acceptedExtensions":["java","go","mod"],
                                              "allowMultipleFiles":true,
                                              "allowSampleRun":true,
                                              "sampleStdinText":"0\\n",
                                              "sampleExpectedStdout":"42\\n",
                                              "timeLimitMs":1500,
                                              "memoryLimitMb":128,
                                              "outputLimitKb":64,
                                              "judgeMode":"STANDARD_IO",
                                              "languageExecutionEnvironments":[
                                                {
                                                  "programmingLanguage":"JAVA21",
                                                  "executionEnvironment":{
                                                    "profileId":%s
                                                  }
                                                },
                                                {
                                                  "programmingLanguage":"GO122",
                                                  "executionEnvironment":{
                                                    "profileId":%s
                                                  }
                                                }
                                              ],
                                              "judgeCases":[
                                                {"stdinText":"0\\n","expectedStdout":"42\\n","score":100}
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
                                        javaProfileId,
                                        goProfileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return readLong(result, "$.id");
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
}
