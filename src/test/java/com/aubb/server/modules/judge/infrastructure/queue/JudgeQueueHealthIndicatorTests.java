package com.aubb.server.modules.judge.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.health.contributor.Status;

class JudgeQueueHealthIndicatorTests {

    @Test
    void reportsUpWhenQueueExists() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        Properties queueProperties = new Properties();
        queueProperties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 3);
        queueProperties.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);
        when(amqpAdmin.getQueueProperties("aubb.judge.jobs")).thenReturn(queueProperties);

        JudgeQueueHealthIndicator indicator = new JudgeQueueHealthIndicator(
                amqpAdmin, new CachingConnectionFactory("rabbit.internal"), "aubb.judge.jobs");

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("queueName", "aubb.judge.jobs")
                .containsEntry("broker", "rabbit.internal:5672")
                .containsEntry("messageCount", 3)
                .containsEntry("consumerCount", 2);
    }

    @Test
    void reportsDownWhenQueueIsMissing() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        when(amqpAdmin.getQueueProperties("aubb.judge.jobs")).thenReturn(null);

        JudgeQueueHealthIndicator indicator = new JudgeQueueHealthIndicator(
                amqpAdmin, new CachingConnectionFactory("rabbit.internal"), "aubb.judge.jobs");

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("queueName", "aubb.judge.jobs")
                .containsEntry("reason", "queue_missing");
    }

    @Test
    void reportsDownWhenRabbitIsUnreachable() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        when(amqpAdmin.getQueueProperties("aubb.judge.jobs"))
                .thenThrow(new AmqpConnectException(new RuntimeException("connection refused")));

        JudgeQueueHealthIndicator indicator = new JudgeQueueHealthIndicator(
                amqpAdmin, new CachingConnectionFactory("rabbit.internal"), "aubb.judge.jobs");

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("queueName", "aubb.judge.jobs")
                .containsEntry("reason", "rabbit_unreachable");
    }
}
