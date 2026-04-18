package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthzOpenApiPermissionCoverageTests extends AbstractIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyBusinessOperationInOpenApiShouldResolveToPermissionOrRule() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode root =
                JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());

        List<String> operations = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        root.path("paths").fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            if (!path.startsWith("/api/v1/")) {
                return;
            }
            pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    return;
                }
                String operation = method + " " + path;
                operations.add(operation);
                if (AuthzOpenApiAccessRegistry.resolve(method, path).isEmpty()) {
                    unmapped.add(operation);
                }
            });
        });

        assertThat(operations)
                .describedAs("运行时 OpenAPI 中应至少存在当前业务基线的 120 个 operation")
                .hasSizeGreaterThanOrEqualTo(120);
        assertThat(unmapped)
                .describedAs("以下运行时 OpenAPI operation 未映射到任何权限点或运行时规则：%s", unmapped)
                .isEmpty();
    }

    @Test
    void registryShouldCoverScopedTeachingAndAuthzAdministrationEndpoints() {
        assertThat(AuthzOpenApiAccessRegistry.resolve("GET", "/api/v1/admin/auth/explain"))
                .contains("auth.explain.read");
        assertThat(AuthzOpenApiAccessRegistry.resolve("POST", "/api/v1/admin/auth/groups"))
                .contains("auth.group.manage");
        assertThat(AuthzOpenApiAccessRegistry.resolve(
                        "POST", "/api/v1/teacher/course-offerings/{offeringId}/members/import"))
                .contains("member.import");
        assertThat(AuthzOpenApiAccessRegistry.resolve("GET", "/api/v1/teacher/submissions/{submissionId}"))
                .contains("submission.read.class|submission.read.offering + submission.code.read_sensitive");
        assertThat(AuthzOpenApiAccessRegistry.resolve(
                        "GET", "/api/v1/teacher/submission-artifacts/{artifactId}/download"))
                .contains("submission.read.class|submission.read.offering + submission.code.read_sensitive");
        assertThat(AuthzOpenApiAccessRegistry.resolve(
                        "GET", "/api/v1/teacher/teaching-classes/{teachingClassId}/gradebook/export"))
                .contains("grade.export.class|grade.export.offering");
    }
}
