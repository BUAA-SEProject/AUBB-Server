package com.aubb.server.modules.judge.infrastructure.queue;

import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

@RequiredArgsConstructor
public class JudgeQueueHealthIndicator implements HealthIndicator {

    private final AmqpAdmin amqpAdmin;
    private final ConnectionFactory connectionFactory;
    private final String queueName;

    @Override
    public Health health() {
        String broker = resolveBroker();
        try {
            Properties queueProperties = amqpAdmin.getQueueProperties(queueName);
            if (queueProperties == null) {
                return Health.down()
                        .withDetail("queueName", queueName)
                        .withDetail("broker", broker)
                        .withDetail("reason", "queue_missing")
                        .build();
            }
            return Health.up()
                    .withDetail("queueName", queueName)
                    .withDetail("broker", broker)
                    .withDetail("messageCount", queueProperties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT))
                    .withDetail("consumerCount", queueProperties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT))
                    .build();
        } catch (AmqpException exception) {
            return Health.down(exception)
                    .withDetail("queueName", queueName)
                    .withDetail("broker", broker)
                    .withDetail("reason", "rabbit_unreachable")
                    .build();
        }
    }

    private String resolveBroker() {
        if (connectionFactory instanceof AbstractConnectionFactory rabbitConnectionFactory) {
            return rabbitConnectionFactory.getHost() + ":" + rabbitConnectionFactory.getPort();
        }
        return "configured";
    }
}
