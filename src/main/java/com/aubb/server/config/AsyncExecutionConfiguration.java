package com.aubb.server.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;

@Configuration
public class AsyncExecutionConfiguration {

    @Bean(name = "notificationFanoutTaskExecutor")
    Executor notificationFanoutTaskExecutor() {
        return new TaskExecutorAdapter(java.util.concurrent.Executors.newFixedThreadPool(2));
    }
}
