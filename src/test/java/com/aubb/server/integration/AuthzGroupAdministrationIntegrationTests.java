package com.aubb.server.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthzGroupAdministrationIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

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
                    auth_sessions,
                    role_bindings,
                    auth_group_members,
                    auth_groups,
                    course_members,
                    teaching_classes,
                    course_offering_college_maps,
                    course_offerings,
                    academic_terms,
                    course_catalogs,
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
        insertUser(2L, "teacher-main", "Teacher Main", "teacher-main@example.com");
        insertUser(2L, "course-admin", "Course Admin", "course-admin@example.com");

        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");
    }

    @Test
    void schoolAdminShouldCreateCustomGroupAssignMemberAndExplainPermission() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        String teacherOldToken = login("teacher-main", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(schoolAdminToken);
        Long offeringId = createOffering(schoolAdminToken, catalogId, termId);

        MvcResult createGroupResult = mockMvc.perform(post("/api/v1/admin/auth/groups")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "templateCode":"grade-corrector",
                                  "scopeType":"OFFERING",
                                  "scopeRefId":%s,
                                  "displayName":"期末纠错组"
                                }
                                """.formatted(offeringId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").value("grade-corrector"))
                .andExpect(jsonPath("$.scopeType").value("OFFERING"))
                .andReturn();
        Long groupId = readLong(createGroupResult, "$.id");

        mockMvc.perform(post("/api/v1/admin/auth/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.userId").value(2));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + teacherOldToken))
                .andExpect(status().isUnauthorized());

        String teacherNewToken = login("teacher-main", "Password123");
        assertTokenContainsPermissionSnapshot(teacherNewToken, "grade.override", "grade-corrector");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + teacherNewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("teacher-main"));

        MvcResult explainResult = mockMvc.perform(get("/api/v1/admin/auth/explain")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("userId", "2")
                        .param("permission", "GRADE_OVERRIDE")
                        .param("scopeType", "OFFERING")
                        .param("scopeRefId", String.valueOf(offeringId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andReturn();
        assertGrantPresent(explainResult, "AUTHZ_GROUP", "grade-corrector");
    }

    @Test
    void courseAdminWithoutAuthGroupManageShouldBeForbiddenToCreateGroup() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(schoolAdminToken);
        Long offeringId = createOffering(schoolAdminToken, catalogId, termId);
        Long courseOrgUnitId = jdbcTemplate.queryForObject(
                "SELECT org_course_unit_id FROM course_offerings WHERE id = ?", Long.class, offeringId);

        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, courseOrgUnitId, "COURSE_ADMIN", "course-admin");

        String courseAdminToken = login("course-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/auth/groups")
                        .header("Authorization", "Bearer " + courseAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "templateCode":"grade-corrector",
                                  "scopeType":"OFFERING",
                                  "scopeRefId":%s,
                                  "displayName":"课程纠错组"
                                }
                                """.formatted(offeringId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void builtInCollegeAdminGroupShouldReachAdminEndpointsWithoutLegacyIdentity() throws Exception {
        insertUser(2L, "group-college-admin", "Group College Admin", "group-college-admin@example.com");
        seedBuiltInGroup("college-admin", "COLLEGE", 2L, 4L);

        String schoolAdminToken = login("school-admin", "Password123");
        String groupedAdminToken = login("group-college-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);

        MvcResult catalogResult = mockMvc.perform(post("/api/v1/admin/course-catalogs")
                        .header("Authorization", "Bearer " + groupedAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "courseCode":"CS201",
                                  "courseName":"编译原理",
                                  "courseType":"REQUIRED",
                                  "credit":3.0,
                                  "totalHours":48,
                                  "departmentUnitId":2,
                                  "description":"组授权学院管理员创建课程目录"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long catalogId = readLong(catalogResult, "$.id");

        MvcResult offeringResult = mockMvc.perform(post("/api/v1/admin/course-offerings")
                        .header("Authorization", "Bearer " + groupedAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "catalogId":%s,
                                  "termId":%s,
                                  "offeringCode":"CS201-2026SP-01",
                                  "offeringName":"编译原理（2026春）",
                                  "primaryCollegeUnitId":2,
                                  "secondaryCollegeUnitIds":[],
                                  "deliveryMode":"HYBRID",
                                  "language":"ZH",
                                  "capacity":80,
                                  "instructorUserIds":[2],
                                  "startAt":"2026-02-20T08:00:00+08:00",
                                  "endAt":"2026-07-10T23:59:59+08:00"
                                }
                                """.formatted(catalogId, termId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long offeringId = readLong(offeringResult, "$.id");

        mockMvc.perform(get("/api/v1/admin/course-offerings/{offeringId}", offeringId)
                        .header("Authorization", "Bearer " + groupedAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringCode").value("CS201-2026SP-01"));
    }

    @Test
    void schoolAdminWithoutRoleBindingsShouldBeForbiddenToCreateGroupAndExplainPermission() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(schoolAdminToken);
        Long offeringId = createOffering(schoolAdminToken, catalogId, termId);

        jdbcTemplate.update("""
                DELETE FROM role_bindings
                WHERE user_id = (SELECT id FROM users WHERE username = 'school-admin')
                """);

        String driftedToken = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/auth/groups")
                        .header("Authorization", "Bearer " + driftedToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "templateCode":"grade-corrector",
                                  "scopeType":"OFFERING",
                                  "scopeRefId":%s,
                                  "displayName":"失效管理员纠错组"
                                }
                                """.formatted(offeringId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/auth/explain")
                        .header("Authorization", "Bearer " + driftedToken)
                        .param("userId", "2")
                        .param("permission", "GRADE_OVERRIDE")
                        .param("scopeType", "OFFERING")
                        .param("scopeRefId", String.valueOf(offeringId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void addMemberShouldReactivateExpiredMembership() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(schoolAdminToken);
        Long offeringId = createOffering(schoolAdminToken, catalogId, termId);
        Long groupId = createGroup(schoolAdminToken, offeringId);

        insertExpiredGroupMember(groupId, 2L);

        mockMvc.perform(post("/api/v1/admin/auth/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.userId").value(2));

        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_group_members WHERE group_id = ? AND user_id = ?",
                Integer.class,
                groupId,
                2L);
        org.assertj.core.api.Assertions.assertThat(memberCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                        "SELECT expires_at FROM auth_group_members WHERE group_id = ? AND user_id = ?",
                        Object.class,
                        groupId,
                        2L))
                .isNull();
    }

    @Test
    void addMemberShouldRejectPastExpiry() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");

        Long termId = createTerm(schoolAdminToken);
        Long catalogId = createCatalog(schoolAdminToken);
        Long offeringId = createOffering(schoolAdminToken, catalogId, termId);
        Long groupId = createGroup(schoolAdminToken, offeringId);

        mockMvc.perform(post("/api/v1/admin/auth/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"userId":2,"expiresAt":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTHZ_GROUP_MEMBER_EXPIRES_AT_INVALID"));
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
                                  "instructorUserIds":[2],
                                  "startAt":"2026-02-20T08:00:00+08:00",
                                  "endAt":"2026-07-10T23:59:59+08:00"
                                }
                                """.formatted(catalogId, termId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private Long createGroup(String token, Long offeringId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "templateCode":"grade-corrector",
                                  "scopeType":"OFFERING",
                                  "scopeRefId":%s,
                                  "displayName":"期末纠错组"
                                }
                                """.formatted(offeringId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.id");
    }

    private void seedBuiltInGroup(String templateCode, String scopeType, Long scopeRefId, Long userId) {
        Long templateId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_group_templates WHERE code = ?", Long.class, templateCode);
        jdbcTemplate.update("""
                INSERT INTO auth_groups (
                    template_id,
                    scope_type,
                    scope_ref_id,
                    display_name,
                    managed_by_system,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, templateId, scopeType, scopeRefId, templateCode + "-seed", false, "ACTIVE");
        Long groupId =
                jdbcTemplate.queryForObject("SELECT currval(pg_get_serial_sequence('auth_groups', 'id'))", Long.class);
        jdbcTemplate.update("""
                INSERT INTO auth_group_members (group_id, user_id, source_type)
                VALUES (?, ?, ?)
                """, groupId, userId, "MANUAL");
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

    @SuppressWarnings("unchecked")
    private void assertGrantPresent(MvcResult result, String expectedSource, String expectedSourceReferenceFragment)
            throws Exception {
        List<Map<String, Object>> grants = JsonPath.read(result.getResponse().getContentAsString(), "$.grants");
        boolean matched = grants.stream()
                .anyMatch(grant -> expectedSource.equals(String.valueOf(grant.get("source")))
                        && String.valueOf(grant.get("sourceReference")).contains(expectedSourceReferenceFragment));
        org.assertj.core.api.Assertions.assertThat(matched)
                .as(
                        "expected grant source=%s sourceReference contains=%s",
                        expectedSource, expectedSourceReferenceFragment)
                .isTrue();
    }

    @SuppressWarnings("unchecked")
    private void assertTokenContainsPermissionSnapshot(
            String accessToken, String expectedPermissionCode, String expectedTemplateCode) {
        String[] tokenParts = accessToken.split("\\.");
        org.assertj.core.api.Assertions.assertThat(tokenParts).hasSize(3);
        String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);

        List<String> permissionCodes = JsonPath.read(payloadJson, "$.permissionCodes");
        List<Map<String, Object>> groupBindings = JsonPath.read(payloadJson, "$.groupBindings");

        boolean hasExpectedBinding = groupBindings.stream()
                .anyMatch(binding -> "AUTHZ_GROUP".equals(String.valueOf(binding.get("source")))
                        && expectedTemplateCode.equals(String.valueOf(binding.get("templateCode"))));

        org.assertj.core.api.Assertions.assertThat(permissionCodes).contains(expectedPermissionCode);
        org.assertj.core.api.Assertions.assertThat(hasExpectedBinding)
                .as("expected AUTHZ_GROUP binding with templateCode=%s", expectedTemplateCode)
                .isTrue();
    }

    private void insertExpiredGroupMember(Long groupId, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO auth_group_members (group_id, user_id, source_type, joined_at, expires_at)
                VALUES (?, ?, 'MANUAL', now() - interval '2 day', now() - interval '1 day')
                """, groupId, userId);
    }
}
