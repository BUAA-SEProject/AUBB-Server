package com.aubb.server.config;

import com.aubb.server.modules.judge.infrastructure.queue.JudgeQueueHealthIndicator;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
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

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
    HealthIndicator judgeQueueHealthIndicator(
            AmqpAdmin amqpAdmin, ConnectionFactory connectionFactory, JudgeQueueProperties properties) {
        return new JudgeQueueHealthIndicator(amqpAdmin, connectionFactory, properties.queueName());
    }

    @ConfigurationProperties(prefix = "aubb.judge.queue")
    public record JudgeQueueProperties(boolean enabled, String queueName, String concurrency) {}
}
