package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class StructuredAssignmentIntegrationTests extends AbstractIntegrationTest {

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
    void teacherManagesQuestionBankAndPublishesStructuredAssignment() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-2026", "2026级一班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long singleChoiceQuestionId = createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"链表单选",
                  "prompt":"单链表头插法的时间复杂度是？",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":10,
                  "options":[
                    {"optionKey":"A","content":"O(1)","correct":true},
                    {"optionKey":"B","content":"O(log n)","correct":false},
                    {"optionKey":"C","content":"O(n)","correct":false}
                  ]
                }
                """);

        createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"树遍历多选",
                  "prompt":"以下哪些属于深度优先遍历？",
                  "questionType":"MULTIPLE_CHOICE",
                  "defaultScore":20,
                  "options":[
                    {"optionKey":"A","content":"前序遍历","correct":true},
                    {"optionKey":"B","content":"中序遍历","correct":true},
                    {"optionKey":"C","content":"层序遍历","correct":false}
                  ]
                }
                """);

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("questionType", "SINGLE_CHOICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("链表单选"));

        Long assignmentId = createStructuredAssignment(teacherToken, offeringId, classId, singleChoiceQuestionId);
        publishAssignment(teacherToken, assignmentId);

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sectionCount").value(3))
                .andExpect(jsonPath("$.paper.questionCount").value(5))
                .andExpect(jsonPath("$.paper.totalScore").value(75))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].sourceQuestionId")
                        .value(singleChoiceQuestionId))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].options[0].correct")
                        .value(true))
                .andExpect(jsonPath("$.paper.sections[2].questions[0].config.supportedLanguages[0]")
                        .value("PYTHON3"))
                .andExpect(jsonPath("$.paper.sections[2].questions[0].config.allowSampleRun")
                        .value(true));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sectionCount").value(3))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].options[0].correct")
                        .doesNotExist())
                .andExpect(jsonPath("$.paper.sections[2].questions[0].config.customJudgeScript")
                        .doesNotExist());

        assertThat(queryForCount("SELECT COUNT(*) FROM question_bank_questions WHERE offering_id = 1"))
                .isEqualTo(2);
        assertThat(queryForCount("SELECT COUNT(*) FROM assignment_questions WHERE assignment_id = 1"))
                .isEqualTo(5);
    }

    @Test
    void studentSubmitsStructuredAssignmentAndReceivesObjectiveScoring() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String studentToken = login("student-a", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-2026", "2026级一班", 2026);
        addMember(teacherToken, offeringId, 4L, "STUDENT", classId);

        Long bankQuestionId = createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"复杂度单选",
                  "prompt":"快速排序平均时间复杂度是？",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":10,
                  "options":[
                    {"optionKey":"A","content":"O(n)","correct":false},
                    {"optionKey":"B","content":"O(n log n)","correct":true},
                    {"optionKey":"C","content":"O(n^2)","correct":false}
                  ]
                }
                """);

        Long assignmentId = createScorableStructuredAssignment(teacherToken, offeringId, classId, bankQuestionId);
        publishAssignment(teacherToken, assignmentId);

        MvcResult detailResult = mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        Long singleChoiceQuestionId = readLong(detailResult, "$.paper.sections[0].questions[0].id");
        Long multipleChoiceQuestionId = readLong(detailResult, "$.paper.sections[0].questions[1].id");
        Long shortAnswerQuestionId = readLong(detailResult, "$.paper.sections[1].questions[0].id");

        mockMvc.perform(post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentText":"legacy body should be rejected for structured assignment"
                                }
                                """))
                .andExpect(status().isBadRequest());

        MvcResult submissionResult = mockMvc.perform(
                        post("/api/v1/me/assignments/{assignmentId}/submissions", assignmentId)
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType("application/json")
                                .content("""
                                {
                                  "answers":[
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["B"]},
                                    {"assignmentQuestionId":%s,"selectedOptionKeys":["A","B"]},
                                    {"assignmentQuestionId":%s,"answerText":"二叉堆插入时需要上滤以维持堆序性。"}
                                  ]
                                }
                                """.formatted(
                                        singleChoiceQuestionId, multipleChoiceQuestionId, shortAnswerQuestionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(3))
                .andExpect(jsonPath("$.answers[0].gradingStatus").value("AUTO_GRADED"))
                .andExpect(jsonPath("$.answers[1].gradingStatus").value("AUTO_GRADED"))
                .andExpect(jsonPath("$.answers[2].gradingStatus").value("PENDING_MANUAL"))
                .andExpect(jsonPath("$.scoreSummary.autoScoredScore").value(30))
                .andExpect(jsonPath("$.scoreSummary.finalScore").value(30))
                .andExpect(jsonPath("$.scoreSummary.maxScore").value(50))
                .andExpect(jsonPath("$.scoreSummary.pendingManualCount").value(1))
                .andReturn();

        Long submissionId = readLong(submissionResult, "$.id");

        mockMvc.perform(get("/api/v1/teacher/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreSummary.autoScoredScore").value(30))
                .andExpect(jsonPath("$.answers[2].answerText").value("二叉堆插入时需要上滤以维持堆序性。"));

        assertThat(queryForCount("SELECT COUNT(*) FROM submission_answers WHERE submission_id = 1"))
                .isEqualTo(3);
    }

    @Test
    void teacherUpdatesAndArchivesQuestionBankQuestionWithoutMutatingPublishedSnapshot() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long classId = createTeachingClass(teacherToken, offeringId, "CLS-2026", "2026级一班", 2026);

        Long bankQuestionId = createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"初始链表单选",
                  "prompt":"链表头插法的时间复杂度是？",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":10,
                  "options":[
                    {"optionKey":"A","content":"O(1)","correct":true},
                    {"optionKey":"B","content":"O(n)","correct":false}
                  ]
                }
                """);

        Long assignmentId = createScorableStructuredAssignment(teacherToken, offeringId, classId, bankQuestionId);
        publishAssignment(teacherToken, assignmentId);

        updateQuestionBankQuestion(teacherToken, bankQuestionId, """
                {
                  "title":"更新后的链表单选",
                  "prompt":"更新后的题面",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":12,
                  "options":[
                    {"optionKey":"A","content":"O(1)","correct":false},
                    {"optionKey":"B","content":"O(n)","correct":true}
                  ]
                }
                """);

        mockMvc.perform(get("/api/v1/teacher/question-bank/questions/{questionId}", bankQuestionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("更新后的链表单选"))
                .andExpect(jsonPath("$.defaultScore").value(12))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.options[1].correct").value(true));

        mockMvc.perform(get("/api/v1/teacher/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paper.sections[0].questions[0].sourceQuestionId")
                        .value(bankQuestionId))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].title").value("初始链表单选"))
                .andExpect(jsonPath("$.paper.sections[0].questions[0].options[0].correct")
                        .value(true));

        archiveQuestionBankQuestion(teacherToken, bankQuestionId);

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].archived").value(true));

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"引用已归档题目的作业",
                                  "description":"不应成功",
                                  "teachingClassId":%s,
                                  "openAt":"2026-04-01T08:00:00+08:00",
                                  "dueAt":"2026-04-30T23:59:59+08:00",
                                  "maxSubmissions":1,
                                  "paper":{
                                    "sections":[
                                      {
                                        "title":"客观题",
                                        "questions":[
                                          {"bankQuestionId":%s,"score":10}
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.formatted(classId, bankQuestionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_BANK_QUESTION_ARCHIVED"));
    }

    @Test
    void teacherTagsQuestionBankQuestionsAndFiltersByTags() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        Long graphQuestionId = createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"图搜索单选",
                  "prompt":"BFS 使用什么数据结构？",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":10,
                  "tags":[" Graph ","search","graph"],
                  "options":[
                    {"optionKey":"A","content":"队列","correct":true},
                    {"optionKey":"B","content":"栈","correct":false}
                  ]
                }
                """);

        createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"树结构单选",
                  "prompt":"完全二叉树适合使用哪种存储？",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":10,
                  "tags":["tree"],
                  "options":[
                    {"optionKey":"A","content":"顺序存储","correct":true},
                    {"optionKey":"B","content":"链式存储","correct":false}
                  ]
                }
                """);

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("tag", "graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(graphQuestionId))
                .andExpect(jsonPath("$.items[0].tags[0]").value("graph"))
                .andExpect(jsonPath("$.items[0].tags[1]").value("search"));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("tag", "graph", "search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(graphQuestionId));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("tag", "graph", "tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        updateQuestionBankQuestion(teacherToken, graphQuestionId, """
                {
                  "title":"图搜索单选（更新）",
                  "prompt":"更新后的题面",
                  "questionType":"SINGLE_CHOICE",
                  "defaultScore":12,
                  "tags":["bfs","graph"],
                  "options":[
                    {"optionKey":"A","content":"队列","correct":true},
                    {"optionKey":"B","content":"栈","correct":false}
                  ]
                }
                """);

        mockMvc.perform(get("/api/v1/teacher/question-bank/questions/{questionId}", graphQuestionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("图搜索单选（更新）"))
                .andExpect(jsonPath("$.tags[0]").value("bfs"))
                .andExpect(jsonPath("$.tags[1]").value("graph"));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("tag", "search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("tag", "bfs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(graphQuestionId));

        assertThat(queryForCount(
                        "SELECT COUNT(*) FROM question_bank_question_tags WHERE question_id = " + graphQuestionId))
                .isEqualTo(2);
    }

    @Test
    void teacherCategorizesQuestionBankQuestionsAndFiltersByCategory() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        Long graphQuestionId = createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"图搜索题",
                  "prompt":"BFS 的访问顺序特点是什么？",
                  "questionType":"SHORT_ANSWER",
                  "defaultScore":15,
                  "categoryName":"图论"
                }
                """);

        createQuestionBankQuestion(teacherToken, offeringId, """
                {
                  "title":"树遍历题",
                  "prompt":"说明前序遍历。",
                  "questionType":"SHORT_ANSWER",
                  "defaultScore":10,
                  "categoryName":"树结构"
                }
                """);

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("category", "图论"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(graphQuestionId))
                .andExpect(jsonPath("$.items[0].categoryName").value("图论"));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/categories", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == '图论')].activeQuestionCount").value(contains(1)))
                .andExpect(jsonPath("$[?(@.name == '树结构')].activeQuestionCount").value(contains(1)));

        updateQuestionBankQuestion(teacherToken, graphQuestionId, """
                {
                  "title":"图搜索题（更新）",
                  "prompt":"更新后的 BFS 题面",
                  "questionType":"SHORT_ANSWER",
                  "defaultScore":20,
                  "categoryName":"搜索算法"
                }
                """);

        mockMvc.perform(get("/api/v1/teacher/question-bank/questions/{questionId}", graphQuestionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("图搜索题（更新）"))
                .andExpect(jsonPath("$.categoryName").value("搜索算法"));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("category", "图论"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("category", "搜索算法"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(graphQuestionId));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/question-bank/categories", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '图论')].activeQuestionCount").value(contains(0)))
                .andExpect(
                        jsonPath("$[?(@.name == '搜索算法')].activeQuestionCount").value(contains(1)));

        assertThat(queryForCount("SELECT COUNT(*) FROM question_bank_categories WHERE offering_id = 1"))
                .isEqualTo(3);
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

    private Long createQuestionBankQuestion(String token, Long offeringId, String body) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/teacher/course-offerings/{offeringId}/question-bank/questions", offeringId)
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private void updateQuestionBankQuestion(String token, Long questionId, String body) throws Exception {
        mockMvc.perform(put("/api/v1/teacher/question-bank/questions/{questionId}", questionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }

    private void archiveQuestionBankQuestion(String token, Long questionId) throws Exception {
        mockMvc.perform(post("/api/v1/teacher/question-bank/questions/{questionId}/archive", questionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Long createStructuredAssignment(String token, Long offeringId, Long classId, Long bankQuestionId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"结构化作业一",
                                  "description":"含题库和多题型",
                                  "teachingClassId":%s,
                                  "openAt":"2026-04-01T08:00:00+08:00",
                                  "dueAt":"2026-04-30T23:59:59+08:00",
                                  "maxSubmissions":3,
                                  "paper":{
                                    "sections":[
                                      {
                                        "title":"客观题",
                                        "description":"自动判分",
                                        "questions":[
                                          {"bankQuestionId":%s,"score":10},
                                          {
                                            "title":"图遍历多选",
                                            "prompt":"以下哪些遍历依赖队列？",
                                            "questionType":"MULTIPLE_CHOICE",
                                            "score":15,
                                            "options":[
                                              {"optionKey":"A","content":"广度优先遍历","correct":true},
                                              {"optionKey":"B","content":"深度优先遍历","correct":false},
                                              {"optionKey":"C","content":"层序遍历","correct":true}
                                            ]
                                          }
                                        ]
                                      },
                                      {
                                        "title":"主观题",
                                        "description":"人工阅卷",
                                        "questions":[
                                          {
                                            "title":"简答题",
                                            "prompt":"说明并查集的路径压缩思想。",
                                            "questionType":"SHORT_ANSWER",
                                            "score":20,
                                            "config":{"referenceAnswer":"递归压缩父节点路径。"}
                                          },
                                          {
                                            "title":"实验报告上传",
                                            "prompt":"提交 PDF 报告。",
                                            "questionType":"FILE_UPLOAD",
                                            "score":10,
                                            "config":{
                                              "maxFileCount":1,
                                              "maxFileSizeMb":20,
                                              "acceptedExtensions":["pdf"]
                                            }
                                          }
                                        ]
                                      },
                                      {
                                        "title":"编程题",
                                        "description":"后续接 judge",
                                        "questions":[
                                          {
                                            "title":"A+B",
                                            "prompt":"读取两个整数并输出和。",
                                            "questionType":"PROGRAMMING",
                                            "score":20,
                                            "config":{
                                              "supportedLanguages":["PYTHON3","JAVA21"],
                                              "allowMultipleFiles":true,
                                              "allowSampleRun":true,
                                              "sampleStdinText":"1 2\\n",
                                              "sampleExpectedStdout":"3\\n",
                                              "timeLimitMs":1000,
                                              "memoryLimitMb":128,
                                              "outputLimitKb":64,
                                              "judgeMode":"CUSTOM_SCRIPT",
                                              "customJudgeScript":"import json\\nprint(json.dumps({\\\"verdict\\\": \\\"ACCEPTED\\\"}))",
                                              "judgeCases":[
                                                {"stdinText":"1 2\\n","expectedStdout":"3\\n","score":20}
                                              ]
                                            }
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.formatted(classId, bankQuestionId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createScorableStructuredAssignment(String token, Long offeringId, Long classId, Long bankQuestionId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"自动评分作业",
                                  "description":"结构化客观题作业",
                                  "teachingClassId":%s,
                                  "openAt":"2026-04-01T08:00:00+08:00",
                                  "dueAt":"2026-04-30T23:59:59+08:00",
                                  "maxSubmissions":3,
                                  "paper":{
                                    "sections":[
                                      {
                                        "title":"客观题",
                                        "questions":[
                                          {"bankQuestionId":%s,"score":10},
                                          {
                                            "title":"遍历多选",
                                            "prompt":"以下哪些遍历会访问每个节点一次？",
                                            "questionType":"MULTIPLE_CHOICE",
                                            "score":20,
                                            "options":[
                                              {"optionKey":"A","content":"前序遍历","correct":true},
                                              {"optionKey":"B","content":"中序遍历","correct":true},
                                              {"optionKey":"C","content":"不存在的遍历","correct":false}
                                            ]
                                          }
                                        ]
                                      },
                                      {
                                        "title":"简答题",
                                        "questions":[
                                          {
                                            "title":"简述堆排序",
                                            "prompt":"简要说明堆排序的核心步骤。",
                                            "questionType":"SHORT_ANSWER",
                                            "score":20,
                                            "config":{"referenceAnswer":"建堆、交换堆顶、下滤。"}
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.formatted(classId, bankQuestionId)))
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
