package com.aubb.server;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsEndpointIsPublicAndContainsStableBusinessPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(content().string(containsString("/api/v1/auth/refresh")))
                .andExpect(content().string(containsString("/api/v1/auth/revoke")))
                .andExpect(content().string(containsString("/api/v1/admin/users")))
                .andExpect(content().string(containsString("/api/v1/me/assignments")))
                .andExpect(content().string(containsString("/api/v1/me/notifications")))
                .andExpect(content().string(containsString("/api/v1/me/notifications/stream")))
                .andExpect(content()
                        .string(containsString("/api/v1/teacher/assignments/{assignmentId}/grade-publish-batches")))
                .andExpect(content().string(containsString("/api/v1/teacher/assignments/{assignmentId}/paper")))
                .andExpect(content().string(containsString("/api/v1/teacher/course-offerings/{offeringId}/labs")));
    }

    @Test
    void swaggerUiIndexIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void noContentEndpointsExpose204InOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/revoke'].post.responses['204']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users/{userId}/sessions/revoke'].post.responses['204']")
                        .exists());
    }
}
