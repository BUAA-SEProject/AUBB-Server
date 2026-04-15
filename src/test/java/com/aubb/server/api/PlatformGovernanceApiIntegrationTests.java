package com.aubb.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class PlatformGovernanceApiIntegrationTests extends AbstractIntegrationTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE audit_logs, user_org_memberships, academic_profiles, user_scope_roles, platform_configs, users, org_units RESTART IDENTITY CASCADE");

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
                1L,
                "school-admin",
                "School Admin",
                "school-admin@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 1L, "SCHOOL_ADMIN", "school-admin");

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
                2L,
                "college-admin",
                "College Admin",
                "college-admin@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 2L, "COLLEGE_ADMIN", "college-admin");
    }

    @Test
    void managesLivePlatformConfigAndWritesAuditTrail() throws Exception {
        String token = login("school-admin", "Password123");

        mockMvc.perform(put("/api/v1/admin/platform-config/current")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "platformName":"AUBB One",
                                  "platformShortName":"A1",
                                  "logoUrl":"https://example.com/logo.png",
                                  "footerText":"Footer 1",
                                  "defaultHomePath":"/admin",
                                  "themeKey":"aubb-light",
                                  "loginNotice":"Welcome 1",
                                  "moduleFlags":{"courses":true,"submissions":true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformName").value("AUBB One"))
                .andExpect(jsonPath("$.themeKey").value("aubb-light"));

        mockMvc.perform(get("/api/v1/admin/platform-config/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformShortName").value("A1"));

        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("PLATFORM_CONFIG_UPDATED"))
                .andExpect(jsonPath("$.items[1].action").value("LOGIN_SUCCESS"));
    }

    @Test
    void enforcesOrganizationHierarchyAndSupportsMultiIdentityUserManagement() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Software Engineering","code":"CRS-SE","type":"COURSE","sortOrder":1,"parentId":2}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"SE Class 1","code":"CLS-SE-1","type":"CLASS","sortOrder":1,"parentId":4}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Invalid Course","code":"CRS-INVALID","type":"COURSE","sortOrder":1,"parentId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORG_HIERARCHY_INVALID"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"dual-admin",
                                  "displayName":"Dual Admin",
                                  "email":"dual-admin@example.com",
                                  "password":"Password123",
                                  "primaryOrgUnitId":5,
                                  "identityAssignments":[
                                    {"roleCode":"COLLEGE_ADMIN","scopeOrgUnitId":2},
                                    {"roleCode":"CLASS_ADMIN","scopeOrgUnitId":5}
                                  ],
                                  "accountStatus":"ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identities.length()").value(2))
                .andExpect(jsonPath("$.identities[0].roleCode").value("CLASS_ADMIN"))
                .andExpect(jsonPath("$.identities[1].roleCode").value("COLLEGE_ADMIN"));

        mockMvc.perform(put("/api/v1/admin/users/3/identities")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "identityAssignments":[
                                    {"roleCode":"COURSE_ADMIN","scopeOrgUnitId":4},
                                    {"roleCode":"CLASS_ADMIN","scopeOrgUnitId":5}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identities.length()").value(2));

        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", """
                        username,displayName,email,password,primaryOrgCode,identities,status
                        class-admin-1,Class Admin One,class-admin-1@example.com,Password123,CLS-SE-1,CLASS_ADMIN@CLS-SE-1,ACTIVE
                        bad-admin,Bad Admin,bad-admin@example.com,Password123,CLS-SE-1,COLLEGE_ADMIN@CLS-SE-1,ACTIVE
                        """.getBytes());

        mockMvc.perform(multipart("/api/v1/admin/users/import")
                        .file(file)
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("importType", "csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.success").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(3));

        assertThat(queryForCount("SELECT COUNT(*) FROM users WHERE username = 'class-admin-1'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM users WHERE username = 'bad-admin'"))
                .isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT result FROM audit_logs WHERE action = 'USER_IMPORTED' ORDER BY id DESC LIMIT 1",
                        String.class))
                .isEqualTo("FAILURE");

        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"accountStatus":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("DISABLED"));
    }

    @Test
    void enforcesScopedAdministrationForCollegeAdmin() throws Exception {
        String collegeAdminToken = login("college-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + collegeAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Engineering Course","code":"CRS-ENG-1","type":"COURSE","sortOrder":1,"parentId":2}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + collegeAdminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Business Course","code":"CRS-BIZ-1","type":"COURSE","sortOrder":1,"parentId":3}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/platform-config/current")
                        .header("Authorization", "Bearer " + collegeAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsUsersWithinAdministratorScope() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (2, 'CRS-ENG-1', 'Engineering Course', 'COURSE', 3, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (4, 'CLS-ENG-1', 'Engineering Class 1', 'CLASS', 4, 1, 'ACTIVE')
                """);
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
                5L,
                "class-admin",
                "Class Admin",
                "class-admin@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 5L, "CLASS_ADMIN", "class-admin");
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
                3L,
                "biz-operator",
                "Biz Operator",
                "biz-operator@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "DISABLED",
                0);

        String schoolAdminToken = login("school-admin", "Password123");
        String collegeAdminToken = login("college-admin", "Password123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("keyword", "class-admin")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("class-admin"))
                .andExpect(jsonPath("$.items[0].primaryOrgUnit.id").value(5))
                .andExpect(jsonPath("$.items[0].primaryOrgUnit.code").value("CLS-ENG-1"))
                .andExpect(jsonPath("$.items[0].primaryOrgUnit.name").value("Engineering Class 1"))
                .andExpect(jsonPath("$.items[0].primaryOrgUnit.type").value("CLASS"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + collegeAdminToken)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].username", containsInAnyOrder("college-admin", "class-admin")));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + collegeAdminToken)
                        .param("orgUnitId", "3"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("accountStatus", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("biz-operator"));
    }

    @Test
    void readsUserDetailWithinAdministratorScope() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (2, 'CRS-ENG-1', 'Engineering Course', 'COURSE', 3, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (4, 'CLS-ENG-1', 'Engineering Class 1', 'CLASS', 4, 1, 'ACTIVE')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    primary_org_unit_id,
                    username,
                    display_name,
                    email,
                    password_hash,
                    account_status,
                    failed_login_attempts,
                    last_login_at,
                    locked_until,
                    expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, now() - interval '2 hour', now() + interval '30 minute', now() + interval '90 day')
                """,
                5L,
                "detail-user",
                "Detail User",
                "detail-user@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "LOCKED",
                3);
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 5L, "CLASS_ADMIN", "detail-user");
        jdbcTemplate.update("""
                INSERT INTO academic_profiles (
                    user_id,
                    academic_id,
                    real_name,
                    identity_type,
                    profile_status
                )
                SELECT id, ?, ?, ?, ? FROM users WHERE username = ?
                """, "20260001", "详情用户", "TEACHER", "ACTIVE", "detail-user");
        jdbcTemplate.update("""
                INSERT INTO user_org_memberships (
                    user_id,
                    org_unit_id,
                    membership_type,
                    membership_status,
                    source_type
                )
                SELECT id, ?, ?, ?, ? FROM users WHERE username = ?
                """, 4L, "TEACHES", "ACTIVE", "MANUAL", "detail-user");
        jdbcTemplate.update("""
                INSERT INTO user_org_memberships (
                    user_id,
                    org_unit_id,
                    membership_type,
                    membership_status,
                    source_type
                )
                SELECT id, ?, ?, ?, ? FROM users WHERE username = ?
                """, 5L, "MANAGES", "ACTIVE", "MANUAL", "detail-user");

        String schoolAdminToken = login("school-admin", "Password123");

        mockMvc.perform(get("/api/v1/admin/users/3").header("Authorization", "Bearer " + schoolAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("detail-user"))
                .andExpect(jsonPath("$.accountStatus").value("LOCKED"))
                .andExpect(jsonPath("$.primaryOrgUnit.id").value(5))
                .andExpect(jsonPath("$.primaryOrgUnit.code").value("CLS-ENG-1"))
                .andExpect(jsonPath("$.primaryOrgUnit.name").value("Engineering Class 1"))
                .andExpect(jsonPath("$.primaryOrgUnit.type").value("CLASS"))
                .andExpect(jsonPath("$.academicProfile.academicId").value("20260001"))
                .andExpect(jsonPath("$.academicProfile.realName").value("详情用户"))
                .andExpect(jsonPath("$.academicProfile.identityType").value("TEACHER"))
                .andExpect(jsonPath("$.identities[0].roleCode").value("CLASS_ADMIN"))
                .andExpect(jsonPath("$.memberships.length()").value(2))
                .andExpect(jsonPath("$.memberships[0].membershipType").value("TEACHES"))
                .andExpect(jsonPath("$.lastLoginAt").exists())
                .andExpect(jsonPath("$.lockedUntil").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void listsUsersByAcademicProfileAndRoleFilters() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (2, 'CRS-ENG-1', 'Engineering Course', 'COURSE', 3, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (4, 'CLS-ENG-1', 'Engineering Class 1', 'CLASS', 4, 1, 'ACTIVE')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    primary_org_unit_id,
                    username,
                    display_name,
                    email,
                    password_hash,
                    account_status,
                    failed_login_attempts,
                    phone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                5L,
                "teacher-one",
                "Teacher One",
                "teacher-one@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0,
                "13800000001");
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    primary_org_unit_id,
                    username,
                    display_name,
                    email,
                    password_hash,
                    account_status,
                    failed_login_attempts,
                    phone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                5L,
                "student-one",
                "Student One",
                "student-one@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0,
                "13800000002");
        jdbcTemplate.update("""
                INSERT INTO academic_profiles (user_id, academic_id, real_name, identity_type, profile_status, phone)
                SELECT id, ?, ?, ?, ?, ? FROM users WHERE username = ?
                """, "T2026001", "张老师", "TEACHER", "ACTIVE", "13800000001", "teacher-one");
        jdbcTemplate.update("""
                INSERT INTO academic_profiles (user_id, academic_id, real_name, identity_type, profile_status, phone)
                SELECT id, ?, ?, ?, ?, ? FROM users WHERE username = ?
                """, "S2026001", "李同学", "STUDENT", "ACTIVE", "13800000002", "student-one");
        jdbcTemplate.update("""
                INSERT INTO user_scope_roles (user_id, scope_org_unit_id, role_code)
                SELECT id, ?, ? FROM users WHERE username = ?
                """, 5L, "CLASS_ADMIN", "teacher-one");

        String schoolAdminToken = login("school-admin", "Password123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("academicId", "T2026001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("teacher-one"))
                .andExpect(jsonPath("$.items[0].academicProfile.realName").value("张老师"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("identityType", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("student-one"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("roleCode", "CLASS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("teacher-one"));
    }

    @Test
    void createsUserWithAcademicProfileAndMemberships() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (2, 'CRS-SE', 'Software Engineering', 'COURSE', 3, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (4, 'CLS-SE-1', 'SE Class 1', 'CLASS', 4, 1, 'ACTIVE')
                """);

        String schoolAdminToken = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"student-created",
                                  "displayName":"Student Created",
                                  "email":"student-created@example.com",
                                  "password":"Password123",
                                  "primaryOrgUnitId":5,
                                  "identityAssignments":[
                                    {"roleCode":"CLASS_ADMIN","scopeOrgUnitId":5}
                                  ],
                                  "accountStatus":"ACTIVE",
                                  "phone":"13800000003",
                                  "academicProfile":{
                                    "academicId":"S2026999",
                                    "realName":"王同学",
                                    "identityType":"STUDENT",
                                    "profileStatus":"ACTIVE",
                                    "phone":"13800000003"
                                  },
                                  "memberships":[
                                    {
                                      "orgUnitId":4,
                                      "membershipType":"ENROLLED",
                                      "membershipStatus":"ACTIVE",
                                      "sourceType":"IMPORT"
                                    },
                                    {
                                      "orgUnitId":5,
                                      "membershipType":"ENROLLED",
                                      "membershipStatus":"ACTIVE",
                                      "sourceType":"IMPORT"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("13800000003"))
                .andExpect(jsonPath("$.academicProfile.academicId").value("S2026999"))
                .andExpect(jsonPath("$.memberships.length()").value(2));

        assertThat(queryForCount("SELECT COUNT(*) FROM academic_profiles WHERE academic_id = 'S2026999'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM user_org_memberships WHERE membership_type = 'ENROLLED'"))
                .isEqualTo(2);
    }

    @Test
    void updatesAcademicProfileAndMemberships() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (2, 'CRS-ENG-1', 'Engineering Course', 'COURSE', 3, 1, 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO org_units (parent_id, code, name, type, level, sort_order, status)
                VALUES (4, 'CLS-ENG-1', 'Engineering Class 1', 'CLASS', 4, 1, 'ACTIVE')
                """);
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
                5L,
                "update-user",
                "Update User",
                "update-user@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);

        String schoolAdminToken = login("school-admin", "Password123");

        mockMvc.perform(put("/api/v1/admin/users/3/profile")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "academicId":"T2026888",
                                  "realName":"赵老师",
                                  "identityType":"TEACHER",
                                  "profileStatus":"ACTIVE",
                                  "phone":"13800000008"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicProfile.academicId").value("T2026888"))
                .andExpect(jsonPath("$.academicProfile.realName").value("赵老师"));

        mockMvc.perform(put("/api/v1/admin/users/3/memberships")
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "memberships":[
                                    {
                                      "orgUnitId":4,
                                      "membershipType":"TEACHES",
                                      "membershipStatus":"ACTIVE",
                                      "sourceType":"MANUAL"
                                    },
                                    {
                                      "orgUnitId":5,
                                      "membershipType":"MANAGES",
                                      "membershipStatus":"ACTIVE",
                                      "sourceType":"MANUAL"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships.length()").value(2))
                .andExpect(jsonPath("$.memberships[0].membershipType").value("TEACHES"));

        assertThat(queryForCount("SELECT COUNT(*) FROM academic_profiles WHERE academic_id = 'T2026888'"))
                .isEqualTo(1);
        assertThat(queryForCount("SELECT COUNT(*) FROM user_org_memberships WHERE user_id = 3"))
                .isEqualTo(2);
    }

    @Test
    void forbidsReadingUserDetailOutsideAdministratorScope() throws Exception {
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
                3L,
                "biz-user",
                "Biz User",
                "biz-user@example.com",
                PASSWORD_ENCODER.encode("Password123"),
                "ACTIVE",
                0);

        String collegeAdminToken = login("college-admin", "Password123");

        mockMvc.perform(get("/api/v1/admin/users/3").header("Authorization", "Bearer " + collegeAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsupportedImportType() throws Exception {
        String schoolAdminToken = login("school-admin", "Password123");
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", """
                        username,displayName,email,password,primaryOrgCode,identities,status
                        demo-user,Demo User,demo-user@example.com,Password123,SCH-1,SCHOOL_ADMIN@SCH-1,ACTIVE
                        """.getBytes());

        mockMvc.perform(multipart("/api/v1/admin/users/import")
                        .file(file)
                        .header("Authorization", "Bearer " + schoolAdminToken)
                        .param("importType", "xlsx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_TYPE_UNSUPPORTED"));
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

    private int queryForCount(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
