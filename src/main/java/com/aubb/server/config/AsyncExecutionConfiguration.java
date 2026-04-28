package com.aubb.server.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AsyncExecutionConfiguration.NotificationFanoutExecutorProperties.class)
public class AsyncExecutionConfiguration {

    @Bean(name = "notificationFanoutTaskExecutor")
    Executor notificationFanoutTaskExecutor(NotificationFanoutExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-fanout-");
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setKeepAliveSeconds(properties.keepAliveSeconds());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @ConfigurationProperties(prefix = "aubb.async.notification-fanout-executor")
    public record NotificationFanoutExecutorProperties(
            int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds) {}
}
