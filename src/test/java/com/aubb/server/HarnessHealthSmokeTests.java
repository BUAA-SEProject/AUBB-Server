package com.aubb.server;

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
class HarnessHealthSmokeTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublicAndHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessEndpointIsPublicAndDoesNotRequireOptionalDependenciesByDefault() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis").doesNotExist())
                .andExpect(jsonPath("$.components.goJudge").doesNotExist())
                .andExpect(jsonPath("$.components.judgeQueue").doesNotExist());
    }

    @Test
    void prometheusEndpointIsPublicAndExposesBusinessMetricNames() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aubb_judge_job_executions_total")))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.containsString("aubb_judge_job_execution_seconds_count")))
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("aubb_grading_grade_publications_total")))
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("aubb_grading_appeal_creations_total")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aubb_grading_appeal_reviews_total")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aubb_judge_queue_depth")));
    }
}
