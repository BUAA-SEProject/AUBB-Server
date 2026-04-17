package com.aubb.server.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "aubb.judge.go-judge.enabled=true"})
@AutoConfigureMockMvc
@Import(com.aubb.server.TestcontainersConfiguration.class)
class JudgeDependencyHealthIntegrationTests extends AbstractRealJudgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readinessEndpointReportsRealJudgeDependencies() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.goJudge.status").value("UP"))
                .andExpect(jsonPath("$.components.goJudge.details.baseUrl").exists())
                .andExpect(jsonPath("$.components.goJudge.details.buildVersion").value("v1.11.4"))
                .andExpect(jsonPath("$.components.judgeQueue.status").value("UP"))
                .andExpect(jsonPath("$.components.judgeQueue.details.queueName").value("aubb.judge.jobs.test"))
                .andExpect(
                        jsonPath("$.components.judgeQueue.details.messageCount").value(0))
                .andExpect(jsonPath("$.components.minioStorage.status").value("UP"))
                .andExpect(jsonPath("$.components.minioStorage.details.bucket").value("aubb-real-go-judge-test"));
    }
}
