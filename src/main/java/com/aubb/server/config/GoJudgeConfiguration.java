package com.aubb.server.config;

import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeClient;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeHealthIndicator;
import com.aubb.server.modules.judge.infrastructure.gojudge.GoJudgeRestClient;
import java.net.URI;
import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties(GoJudgeConfiguration.GoJudgeProperties.class)
public class GoJudgeConfiguration {

    @Bean
    RestClient goJudgeRestClient(RestClient.Builder builder, GoJudgeProperties properties) {
        return builder.baseUrl(properties.baseUrl().toString()).build();
    }

    @Bean
    GoJudgeClient goJudgeClient(RestClient goJudgeRestClient) {
        return new GoJudgeRestClient(goJudgeRestClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.go-judge", name = "enabled", havingValue = "true")
    HealthIndicator goJudgeHealthIndicator(RestClient goJudgeRestClient, GoJudgeProperties properties) {
        return new GoJudgeHealthIndicator(goJudgeRestClient, properties.baseUrl());
    }

    @Bean(name = "judgeExecutionTaskExecutor")
    Executor judgeExecutionTaskExecutor() {
        return new TaskExecutorAdapter(java.util.concurrent.Executors.newFixedThreadPool(4));
    }

    @ConfigurationProperties(prefix = "aubb.judge.go-judge")
    public record GoJudgeProperties(boolean enabled, URI baseUrl) {}
}
