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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
    GoJudgeConfiguration.GoJudgeProperties.class,
    GoJudgeConfiguration.JudgeExecutionExecutorProperties.class
})
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
    Executor judgeExecutionTaskExecutor(JudgeExecutionExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("judge-local-");
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setKeepAliveSeconds(properties.keepAliveSeconds());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @ConfigurationProperties(prefix = "aubb.judge.go-judge")
    public record GoJudgeProperties(boolean enabled, URI baseUrl) {}

    @ConfigurationProperties(prefix = "aubb.judge.local-execution-executor")
    public record JudgeExecutionExecutorProperties(
            int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds) {}
}
