package com.aubb.server.config;

import com.aubb.server.modules.judge.infrastructure.queue.JudgeQueueHealthIndicator;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
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
        return QueueBuilder.durable(properties.queueName())
                .deadLetterExchange(properties.dlqExchange())
                .deadLetterRoutingKey(properties.dlqQueueName())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
    DirectExchange judgeExecutionDeadLetterExchange(JudgeQueueProperties properties) {
        return new DirectExchange(properties.dlqExchange(), true, false);
    }

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
    Queue judgeExecutionDeadLetterQueue(JudgeQueueProperties properties) {
        return QueueBuilder.durable(properties.dlqQueueName()).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
    Binding judgeExecutionDeadLetterBinding(
            Queue judgeExecutionDeadLetterQueue,
            DirectExchange judgeExecutionDeadLetterExchange,
            JudgeQueueProperties properties) {
        return BindingBuilder.bind(judgeExecutionDeadLetterQueue)
                .to(judgeExecutionDeadLetterExchange)
                .with(properties.dlqQueueName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
    HealthIndicator judgeQueueHealthIndicator(
            AmqpAdmin amqpAdmin, ConnectionFactory connectionFactory, JudgeQueueProperties properties) {
        return new JudgeQueueHealthIndicator(amqpAdmin, connectionFactory, properties.queueName());
    }

    @Bean(name = "judgeQueueListenerContainerFactory")
    @ConditionalOnProperty(
            prefix = "aubb.judge.queue",
            name = {"enabled", "consumer-enabled"},
            havingValue = "true")
    SimpleRabbitListenerContainerFactory judgeQueueListenerContainerFactory(
            ConnectionFactory connectionFactory, JudgeQueueProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        int concurrentConsumers = parseConcurrentConsumers(properties.concurrency());
        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(concurrentConsumers);
        factory.setPrefetchCount(properties.prefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(judgeQueueRetryAdvice(properties));
        return factory;
    }

    private Advice judgeQueueRetryAdvice(JudgeQueueProperties properties) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(Math.max(properties.maxAttempts() - 1, 0))
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    private int parseConcurrentConsumers(String concurrency) {
        if (concurrency == null || concurrency.isBlank()) {
            return 1;
        }
        String normalized =
                concurrency.contains("-") ? concurrency.substring(0, concurrency.indexOf('-')) : concurrency;
        return Math.max(Integer.parseInt(normalized.trim()), 1);
    }

    @ConfigurationProperties(prefix = "aubb.judge.queue")
    public record JudgeQueueProperties(
            boolean enabled,
            boolean publisherEnabled,
            boolean consumerEnabled,
            String queueName,
            String dlqQueueName,
            String dlqExchange,
            String concurrency,
            int prefetch,
            int maxAttempts) {}
}
