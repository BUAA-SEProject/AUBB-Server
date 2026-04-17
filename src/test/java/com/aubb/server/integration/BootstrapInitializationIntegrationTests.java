package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aubb.server.TestcontainersConfiguration;
import com.aubb.server.config.PlatformBootstrapProperties;
import com.aubb.server.modules.platformconfig.application.bootstrap.PlatformBootstrapApplicationService;
import com.aubb.server.modules.platformconfig.application.bootstrap.PlatformBootstrapResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "aubb.bootstrap.enabled=true",
            "aubb.bootstrap.school.code=SCH-1",
            "aubb.bootstrap.school.name=AUBB School",
            "aubb.bootstrap.admin.username=school-admin",
            "aubb.bootstrap.admin.display-name=School Admin",
            "aubb.bootstrap.admin.email=school-admin@example.com",
            "aubb.bootstrap.admin.password=Password123",
            "aubb.bootstrap.admin.academic-id=AUBB-ADMIN-001",
            "aubb.bootstrap.admin.real-name=学校管理员"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BootstrapInitializationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformBootstrapApplicationService platformBootstrapApplicationService;

    @Autowired
    private PlatformBootstrapProperties platformBootstrapProperties;

    @Test
    void bootstrapsFirstSchoolAdminAndPlatformConfigOnStartup() throws Exception {
        assertThat(count("org_units")).isEqualTo(1);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("user_scope_roles")).isEqualTo(1);
        assertThat(count("academic_profiles")).isEqualTo(1);
        assertThat(count("platform_configs")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT code FROM org_units WHERE parent_id IS NULL AND type = 'SCHOOL' LIMIT 1", String.class))
                .isEqualTo("SCH-1");
        assertThat(jdbcTemplate.queryForObject("SELECT username FROM users LIMIT 1", String.class))
                .isEqualTo("school-admin");
        assertThat(jdbcTemplate.queryForObject("SELECT role_code FROM user_scope_roles LIMIT 1", String.class))
                .isEqualTo("SCHOOL_ADMIN");
        assertThat(jdbcTemplate.queryForObject("SELECT platform_name FROM platform_configs LIMIT 1", String.class))
                .isEqualTo("AUBB School");

        String accessToken = login("school-admin", "Password123");

        mockMvc.perform(get("/api/v1/admin/platform-config/current").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformName").value("AUBB School"))
                .andExpect(jsonPath("$.platformShortName").value("SCH-1"));
    }

    @Test
    void rerunningBootstrapIsIdempotentAndSecondSchoolRootIsRejected() throws Exception {
        long orgUnitsBefore = count("org_units");
        long usersBefore = count("users");
        long rolesBefore = count("user_scope_roles");
        long profilesBefore = count("academic_profiles");
        long configsBefore = count("platform_configs");

        PlatformBootstrapResult result = platformBootstrapApplicationService.bootstrap(platformBootstrapProperties);

        assertThat(result.schoolCreated()).isFalse();
        assertThat(result.adminCreated()).isFalse();
        assertThat(result.schoolAdminRoleCreated()).isFalse();
        assertThat(result.academicProfileCreated()).isFalse();
        assertThat(result.platformConfigCreated()).isFalse();
        assertThat(count("org_units")).isEqualTo(orgUnitsBefore);
        assertThat(count("users")).isEqualTo(usersBefore);
        assertThat(count("user_scope_roles")).isEqualTo(rolesBefore);
        assertThat(count("academic_profiles")).isEqualTo(profilesBefore);
        assertThat(count("platform_configs")).isEqualTo(configsBefore);

        String accessToken = login("school-admin", "Password123");

        mockMvc.perform(post("/api/v1/admin/org-units")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Another School","code":"SCH-2","type":"SCHOOL","sortOrder":2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORG_ROOT_ALREADY_EXISTS"));
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
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
}
