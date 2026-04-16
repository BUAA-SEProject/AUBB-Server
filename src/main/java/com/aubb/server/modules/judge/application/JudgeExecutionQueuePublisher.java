package com.aubb.server.modules.judge.application;

import com.aubb.server.config.JudgeQueueConfiguration.JudgeQueueProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "true")
class JudgeExecutionQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JudgeQueueProperties properties;

    JudgeExecutionQueuePublisher(RabbitTemplate rabbitTemplate, JudgeQueueProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJudgeExecutionRequested(JudgeExecutionRequestedEvent event) {
        rabbitTemplate.convertAndSend(properties.queueName(), String.valueOf(event.judgeJobId()));
    }
}
