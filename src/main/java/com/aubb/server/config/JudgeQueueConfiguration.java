package com.aubb.server.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(JudgeQueueConfiguration.JudgeQueueProperties.class)
public class JudgeQueueConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
    Queue judgeExecutionQueue(JudgeQueueProperties properties) {
        return new Queue(properties.queueName(), true);
    }

    @ConfigurationProperties(prefix = "aubb.judge.queue")
    public record JudgeQueueProperties(boolean enabled, String queueName, String concurrency) {}
}
