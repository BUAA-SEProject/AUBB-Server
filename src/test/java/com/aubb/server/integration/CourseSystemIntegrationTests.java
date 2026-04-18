package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationContext;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceType;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResult;
import com.aubb.server.modules.identityaccess.application.authz.core.PermissionAuthorizationService;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.List;
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

class CourseSystemIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PermissionAuthorizationService permissionAuthorizationService;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisIntegrationTestSupport.registerRedisProperties(registry);
        registry.add("aubb.redis.rate-limit.enabled", () -> "false");
        registry.add("aubb.redis.cache.my-courses-ttl", () -> "PT5M");
    }

    @BeforeEach
    void setUp() {
        RedisIntegrationTestSupport.flushAll();
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_logs,
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
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (1, 'COL-BIZ', 'Business', 'COLLEGE', 2, 2, 'ACTIVE')
                """);

        insertUser(1L, "school-admin", "School Admin", "school-admin@example.com");
        insertUser(2L, "eng-admin", "Engineering Admin", "eng-admin@example.com");
        insertUser(3L, "biz-admin", "Business Admin", "biz-admin@example.com");
        insertUser(2L, "teacher-main", "Teacher Main", "teacher-main@example.com");
        insertUser(2L, "ta-mixed", "Ta Mixed", "ta-mixed@example.com");
        insertUser(2L, "student-24", "Student 24", "student-24@example.com");
        insertUser(2L, "student-25", "Student 25", "student-25@example.com");
        insertUser(2L, "student-import", "Student Import", "student-import@example.com");

        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 2L, "COLLEGE_ADMIN", "eng-admin");
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 3L, "COLLEGE_ADMIN", "biz-admin");

        insertAcademicProfile("teacher-main", "T-1001", "张教师", "TEACHER");
        insertAcademicProfile("ta-mixed", "S-2001", "李助教", "STUDENT");
        insertAcademicProfile("student-24", "S-2024", "24级学生", "STUDENT");
        insertAcademicProfile("student-25", "S-2025", "25级学生", "STUDENT");
        insertAcademicProfile("student-import", "S-2026", "导入学生", "STUDENT");
    }

    @Test
    void collegeAdminCreatesSharedCourseOfferingAndLinkedCollegeAdminCanReadIt() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String bizAdminToken = login("biz-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Arts","code":"COL-ART","type":"COLLEGE","sortOrder":3,"parentId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COLLEGE"));

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        mockMvc.perform(get("/api/v1/admin/course-offerings/{offeringId}", offeringId)
                        .header("Authorization", "Bearer " + bizAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringCode").value("CS101-2026SP-01"))
                .andExpect(jsonPath("$.managingColleges[*].code", containsInAnyOrder("COL-ENG", "COL-BIZ")))
                .andExpect(jsonPath("$.instructors[0].username").value("teacher-main"));

        mockMvc.perform(get("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + bizAdminToken)
                        .param("collegeUnitId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].offeringCode").value("CS101-2026SP-01"));
    }

    @Test
    void collegeAdminCannotReadUnsharedOfferingFromAnotherCollege() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String bizAdminToken = login("biz-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        MvcResult offeringResult = mockMvc.perform(post("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + engAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "catalogId":%s,
                                  "termId":%s,
                                  "offeringCode":"CS101-2026SP-ENG",
                                  "offeringName":"工程学院独占开课",
                                  "primaryCollegeUnitId":2,
                                  "secondaryCollegeUnitIds":[],
                                  "deliveryMode":"HYBRID",
                                  "language":"ZH",
                                  "capacity":120,
                                  "instructorUserIds":[4],
                                  "startAt":"2026-02-20T08:00:00+08:00",
                                  "endAt":"2026-07-10T23:59:59+08:00"
                                }
                                """.formatted(catalogId, termId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long offeringId = readLong(offeringResult, "$.id");

        mockMvc.perform(get("/api/v1/admin/course-offerings/{offeringId}", offeringId)
                        .header("Authorization", "Bearer " + bizAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + bizAdminToken)
                        .param("collegeUnitId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void initialInstructorRoleBindingShouldExposeMemberManageGrant() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        Integer bindingCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM role_bindings rb
                JOIN roles r ON r.id = rb.role_id
                WHERE rb.user_id = 4
                  AND rb.scope_type = 'offering'
                  AND rb.scope_id = ?
                  AND rb.status = 'ACTIVE'
                  AND r.code = 'offering_teacher'
                """,
                Integer.class,
                offeringId);
        Integer grantCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM role_bindings rb
                JOIN roles r ON r.id = rb.role_id
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE rb.user_id = 4
                  AND rb.scope_type = 'offering'
                  AND rb.scope_id = ?
                  AND rb.status = 'ACTIVE'
                  AND r.code = 'offering_teacher'
                  AND p.code = 'member.manage'
                """,
                Integer.class,
                offeringId);

        assertThat(bindingCount).isEqualTo(1);
        assertThat(grantCount).isEqualTo(1);
    }

    @Test
    void initialInstructorShouldBeAuthorizedToManageMembersByPermissionCore() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                4L,
                "teacher-main",
                "Teacher Main",
                2L,
                null,
                AccountStatus.ACTIVE,
                null,
                List.of(),
                List.of(),
                java.util.Set.of(),
                null,
                false);
        AuthorizationResult result = permissionAuthorizationService.authorize(
                principal,
                "member.manage",
                new AuthorizationResourceRef(AuthorizationResourceType.OFFERING, offeringId),
                AuthorizationContext.of(OffsetDateTime.now()));

        assertThat(result.allowed()).withFailMessage(result.reasonCode()).isTrue();
    }

    @Test
    void organizationWriteAuditShouldCaptureDecisionAndScope() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Science","code":"COL-SCI","type":"COLLEGE","sortOrder":3,"parentId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COLLEGE"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT decision FROM audit_logs WHERE action = 'ORG_UNIT_CREATED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("ALLOW");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT scope_type FROM audit_logs WHERE action = 'ORG_UNIT_CREATED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("school");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT scope_id FROM audit_logs WHERE action = 'ORG_UNIT_CREATED' ORDER BY id DESC LIMIT 1",
                        Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'requestedType' FROM audit_logs WHERE action = 'ORG_UNIT_CREATED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("COLLEGE");
    }

    @Test
    void archivedOfferingShouldStayReadableButRejectTeachingWrites() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId, "CS101-2026SP-ARCH", "归档开课");

        jdbcTemplate.update(
                "UPDATE course_offerings SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", offeringId);

        mockMvc.perform(get("/api/v1/admin/course-offerings/{offeringId}", offeringId)
                        .header("Authorization", "Bearer " + engAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "classCode":"CLS-ARCH",
                                  "className":"归档班级",
                                  "entryYear":2026,
                                  "capacity":60,
                                  "scheduleSummary":"周二 1-2 节"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void offeringSummaryShouldIncludeClassInstructorInInstructorList() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":5,"memberRole":"CLASS_INSTRUCTOR","teachingClassId":%s,"remark":"班级教师"}
                                  ]
                                }
                                """.formatted(class2024Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failCount").value(0));

        mockMvc.perform(get("/api/v1/admin/course-offerings/{offeringId}", offeringId)
                        .header("Authorization", "Bearer " + engAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructors[*].username", containsInAnyOrder("teacher-main", "ta-mixed")));
    }

    @Test
    void teacherCreatesDifferentYearClassesAndTogglesClassFeatures() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(put("/api/v1/teacher/course-classes/{teachingClassId}/features", class2024Id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "announcementEnabled":false,
                                  "discussionEnabled":false,
                                  "resourceEnabled":true,
                                  "labEnabled":true,
                                  "assignmentEnabled":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.announcementEnabled").value(false))
                .andExpect(jsonPath("$.features.discussionEnabled").value(false));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %s)].entryYear".formatted(class2024Id))
                        .exists())
                .andExpect(jsonPath("$[?(@.id == %s)].entryYear".formatted(class2025Id))
                        .exists())
                .andExpect(jsonPath("$[0].offeringId").value(offeringId));

        assertThat(queryForCount("SELECT COUNT(*) FROM org_units WHERE parent_id = 4 AND type = 'CLASS'"))
                .isEqualTo(2);
    }

    @Test
    void teacherBatchAddsAndImportsExistingUsersOnly() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":6,"memberRole":"STUDENT","teachingClassId":%s,"remark":"24级学生"},
                                    {"userId":5,"memberRole":"TA","teachingClassId":%s,"remark":"25级助教"}
                                  ]
                                }
                                """.formatted(class2024Id, class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failCount").value(0));

        MockMultipartFile file = new MockMultipartFile("file", "course-members.csv", "text/csv", """
                        username,memberRole,classCode,remark
                        student-25,STUDENT,CLS-2025,25级学生
                        missing-user,STUDENT,CLS-2025,缺失用户
                        """.getBytes());
        mockMvc.perform(multipart("/api/v1/teacher/course-offerings/{offeringId}/members/import", offeringId)
                        .file(file)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("importType", "csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.success").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].username").value("missing-user"));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/members", offeringId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4));

        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = 1"))
                .isEqualTo(4);
        assertThat(
                        queryForCount(
                                "SELECT COUNT(*) FROM user_org_memberships WHERE membership_type IN ('TEACHES', 'ASSISTS', 'ENROLLED')"))
                .isEqualTo(4);
        assertThat(queryForCount("SELECT COUNT(*) FROM users WHERE username = 'missing-user'"))
                .isEqualTo(0);
    }

    @Test
    void taCanBeStudentInOneClassAndTaInAnotherButPermissionIsLimited() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taToken = login("ta-mixed", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":5,"memberRole":"STUDENT","teachingClassId":%s,"remark":"作为学生"},
                                    {"userId":5,"memberRole":"TA","teachingClassId":%s,"remark":"作为助教"},
                                    {"userId":7,"memberRole":"STUDENT","teachingClassId":%s,"remark":"同班学生"}
                                  ]
                                }
                                """.formatted(class2024Id, class2025Id, class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3));

        String refreshedTaToken = login("ta-mixed", "Password123");

        mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + refreshedTaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].offeringCode").value("CS101-2026SP-01"))
                .andExpect(jsonPath("$[0].roles[*]", containsInAnyOrder("STUDENT", "TA")))
                .andExpect(jsonPath("$[0].classes[*].classCode", containsInAnyOrder("CLS-2024", "CLS-2025")));

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/members", offeringId)
                        .header("Authorization", "Bearer " + refreshedTaToken)
                        .param("teachingClassId", String.valueOf(class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(put("/api/v1/teacher/course-classes/{teachingClassId}/features", class2025Id)
                        .header("Authorization", "Bearer " + refreshedTaToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "announcementEnabled":false,
                                  "discussionEnabled":false,
                                  "resourceEnabled":true,
                                  "labEnabled":true,
                                  "assignmentEnabled":true
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + refreshedTaToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":8,"memberRole":"STUDENT","teachingClassId":%s,"remark":"助教越权添加"}
                                  ]
                                }
                                """.formatted(class2025Id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameUserCannotBeTeacherAndStudentInSameOffering() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":6,"memberRole":"STUDENT","teachingClassId":%s,"remark":"作为学生"},
                                    {"userId":6,"memberRole":"CLASS_INSTRUCTOR","teachingClassId":%s,"remark":"同时设为班级教师"}
                                  ]
                                }
                                """.formatted(class2024Id, class2024Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failCount").value(1));

        assertThat(queryForCount(
                        "SELECT COUNT(*) FROM course_members WHERE offering_id = " + offeringId + " AND user_id = 6"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 6"
                        + " AND member_role = 'CLASS_INSTRUCTOR'"))
                .isEqualTo(0);
    }

    @Test
    void memberListingAppliesKeywordPaginationAndTaVisibility() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        String taToken = login("ta-mixed", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":6,"memberRole":"STUDENT","teachingClassId":%s,"remark":"24级学生"},
                                    {"userId":7,"memberRole":"STUDENT","teachingClassId":%s,"remark":"25级学生"},
                                    {"userId":8,"memberRole":"STUDENT","teachingClassId":%s,"remark":"导入学生"},
                                    {"userId":5,"memberRole":"TA","teachingClassId":%s,"remark":"25级助教"}
                                  ]
                                }
                                """.formatted(class2024Id, class2025Id, class2025Id, class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(4));

        taToken = login("ta-mixed", "Password123");

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/members", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .param("memberRole", "STUDENT")
                        .param("keyword", "student")
                        .param("page", "2")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].user.username").value("student-import"))
                .andExpect(jsonPath("$.items[0].user.email").value("student-import@example.com"))
                .andExpect(jsonPath("$.items[0].classCode").value("CLS-2025"));

        MvcResult taResult = mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/members", offeringId)
                        .header("Authorization", "Bearer " + taToken)
                        .param("teachingClassId", String.valueOf(class2025Id))
                        .param("memberRole", "STUDENT")
                        .param("keyword", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].user.username", containsInAnyOrder("student-25", "student-import")))
                .andExpect(jsonPath("$.items[*].classCode", containsInAnyOrder("CLS-2025", "CLS-2025")))
                .andReturn();

        Object firstEmail = JsonPath.read(taResult.getResponse().getContentAsString(), "$.items[0].user.email");
        Object secondEmail = JsonPath.read(taResult.getResponse().getContentAsString(), "$.items[1].user.email");
        assertThat(firstEmail).isNull();
        assertThat(secondEmail).isNull();
    }

    @Test
    void teacherCanUpdateMemberStatusAndInvalidateAffectedUserSessions() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-DROP", "退课班", 2024);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":6,"memberRole":"STUDENT","teachingClassId":%s,"remark":"在读学生"}
                                  ]
                                }
                                """.formatted(class2024Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        String studentToken = login("student-24", "Password123");
        Long memberId = findMemberId(offeringId, 6L, "STUDENT");

        mockMvc.perform(patch(
                                "/api/v1/teacher/course-offerings/{offeringId}/members/{memberId}/status",
                                offeringId,
                                memberId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {"memberStatus":"DROPPED","remark":"教师退课处理"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId))
                .andExpect(jsonPath("$.memberStatus").value("DROPPED"))
                .andExpect(jsonPath("$.leftAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isUnauthorized());

        String refreshedStudentToken = login("student-24", "Password123");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refreshedStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student-24"));

        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 6"
                        + " AND member_status = 'DROPPED'"))
                .isEqualTo(1);
        assertThat(
                        queryForCount(
                                "SELECT COUNT(*) FROM auth_sessions WHERE user_id = 6 AND revoked_reason = 'COURSE_MEMBER_STATUS_CHANGED'"))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'currentStatus' FROM audit_logs WHERE action = 'COURSE_MEMBER_STATUS_CHANGED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("DROPPED");
    }

    @Test
    void teacherCanTransferStudentAndPreserveHistoryRows() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":8,"memberRole":"STUDENT","teachingClassId":%s,"remark":"待转班学生"}
                                  ]
                                }
                                """.formatted(class2024Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1));

        String studentToken = login("student-import", "Password123");
        Long memberId = findMemberId(offeringId, 8L, "STUDENT");

        mockMvc.perform(post(
                                "/api/v1/teacher/course-offerings/{offeringId}/members/{memberId}/transfer",
                                offeringId,
                                memberId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {"targetTeachingClassId":%s,"remark":"转入 25 级班"}
                                """.formatted(class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingClassId").value(class2025Id))
                .andExpect(jsonPath("$.classCode").value("CLS-2025"))
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isUnauthorized());

        String refreshedStudentToken = login("student-import", "Password123");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refreshedStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student-import"));

        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 8"
                        + " AND member_role = 'STUDENT'"))
                .isEqualTo(2);
        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 8"
                        + " AND teaching_class_id = "
                        + class2024Id
                        + " AND member_status = 'TRANSFERRED'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM course_members WHERE offering_id = "
                        + offeringId
                        + " AND user_id = 8"
                        + " AND teaching_class_id = "
                        + class2025Id
                        + " AND member_status = 'ACTIVE'"))
                .isEqualTo(1);
        assertThat(
                        queryForCount(
                                "SELECT COUNT(*) FROM auth_sessions WHERE user_id = 8 AND revoked_reason = 'COURSE_MEMBER_TRANSFERRED'"))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'fromTeachingClassId' FROM audit_logs WHERE action = 'COURSE_MEMBER_TRANSFERRED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo(String.valueOf(class2024Id));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT metadata->>'toTeachingClassId' FROM audit_logs WHERE action = 'COURSE_MEMBER_TRANSFERRED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo(String.valueOf(class2025Id));
    }

    @Test
    void classTaCanReadOnlyOwnedTeachingClasses() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        insertUser(2L, "ta-only", "Ta Only", "ta-only@example.com");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":9,"memberRole":"TA","teachingClassId":%s,"remark":"25级助教"},
                                    {"userId":6,"memberRole":"STUDENT","teachingClassId":%s,"remark":"24级学生"},
                                    {"userId":7,"memberRole":"STUDENT","teachingClassId":%s,"remark":"25级学生"}
                                  ]
                                }
                                """.formatted(class2025Id, class2024Id, class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3));

        String taOnlyToken = login("ta-only", "Password123");

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/classes", offeringId)
                        .header("Authorization", "Bearer " + taOnlyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(class2025Id))
                .andExpect(jsonPath("$[0].classCode").value("CLS-2025"));
    }

    @Test
    void classTaMemberListingWithoutClassFilterReturnsOnlyOwnedClassMembers() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");
        insertUser(2L, "ta-only", "Ta Only", "ta-only@example.com");
        insertAcademicProfile("ta-only", "T-3001", "独立助教", "TEACHER");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);
        Long class2024Id = createTeachingClass(teacherToken, offeringId, "CLS-2024", "24级班", 2024);
        Long class2025Id = createTeachingClass(teacherToken, offeringId, "CLS-2025", "25级班", 2025);

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/members/batch", offeringId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "members":[
                                    {"userId":9,"memberRole":"TA","teachingClassId":%s,"remark":"25级助教"},
                                    {"userId":6,"memberRole":"STUDENT","teachingClassId":%s,"remark":"24级学生"},
                                    {"userId":7,"memberRole":"STUDENT","teachingClassId":%s,"remark":"25级学生"}
                                  ]
                                }
                                """.formatted(class2025Id, class2024Id, class2025Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3));

        String taOnlyToken = login("ta-only", "Password123");

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/members", offeringId)
                        .header("Authorization", "Bearer " + taOnlyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].user.username", containsInAnyOrder("ta-only", "student-25")))
                .andExpect(jsonPath("$.items[*].classCode", containsInAnyOrder("CLS-2025", "CLS-2025")));
    }

    @Test
    void myCoursesSummaryCachesHitsAndEvictsAfterOfferingMutations() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String engAdminToken = login("eng-admin", "Password123");
        String teacherToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(engAdminToken);
        Long offeringId = createOffering(engAdminToken, catalogId, termId);

        MvcResult firstResult = mockMvc.perform(
                        get("/api/v1/me/courses").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        assertThat(JsonPath.<String>read(firstResult.getResponse().getContentAsString(), "$[0].offeringName"))
                .isEqualTo("数据结构（2026春）");

        jdbcTemplate.update(
                "UPDATE course_offerings SET offering_name = ?, updated_at = now() WHERE id = ?",
                "数据结构（缓存未驱逐前）",
                offeringId);

        MvcResult cachedResult = mockMvc.perform(
                        get("/api/v1/me/courses").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        assertThat(JsonPath.<String>read(cachedResult.getResponse().getContentAsString(), "$[0].offeringName"))
                .isEqualTo("数据结构（2026春）");

        createOffering(engAdminToken, catalogId, termId, "CS101-2026SP-02", "数据结构（第二开课）");

        MvcResult refreshedResult = mockMvc.perform(
                        get("/api/v1/me/courses").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        assertThat(JsonPath.<java.util.List<String>>read(
                        refreshedResult.getResponse().getContentAsString(),
                        "$[?(@.offeringCode == 'CS101-2026SP-01')].offeringName"))
                .containsExactly("数据结构（缓存未驱逐前）");
        assertThat(JsonPath.<java.util.List<String>>read(
                        refreshedResult.getResponse().getContentAsString(),
                        "$[?(@.offeringCode == 'CS101-2026SP-02')].offeringName"))
                .containsExactly("数据结构（第二开课）");

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(
                        result -> assertThat(result.getResponse().getContentAsString())
                                .contains(
                                        "aubb_cache_operations_total{cache=\"myCoursesSummary\",operation=\"get\",result=\"hit\"}")
                                .contains(
                                        "aubb_cache_operations_total{cache=\"myCoursesSummary\",operation=\"evict\",result=\"success\"}"));
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

    private void insertAcademicProfile(String username, String academicId, String realName, String identityType) {
        jdbcTemplate.update("""
                INSERT INTO academic_profiles (user_id, academic_id, real_name, identity_type, profile_status)
                SELECT id, ?, ?, ?, ? FROM users WHERE username = ?
                """, academicId, realName, identityType, "ACTIVE", username);
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
        return createOffering(token, catalogId, termId, "CS101-2026SP-01", "数据结构（2026春）");
    }

    private Long createOffering(String token, Long catalogId, Long termId, String offeringCode, String offeringName)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "catalogId":%s,
                                  "termId":%s,
                                  "offeringCode":"%s",
                                  "offeringName":"%s",
                                  "primaryCollegeUnitId":2,
                                  "secondaryCollegeUnitIds":[3],
                                  "deliveryMode":"HYBRID",
                                  "language":"ZH",
                                  "capacity":120,
                                  "instructorUserIds":[4],
                                  "startAt":"2026-02-20T08:00:00+08:00",
                                  "endAt":"2026-07-10T23:59:59+08:00"
                                }
                                """.formatted(catalogId, termId, offeringCode, offeringName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
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
                .andExpect(jsonPath("$.classCode").value(classCode))
                .andExpect(jsonPath("$.entryYear").value(entryYear))
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
