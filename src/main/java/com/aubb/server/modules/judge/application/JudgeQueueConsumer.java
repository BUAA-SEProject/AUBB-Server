package com.aubb.server.modules.judge.application;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "aubb.judge.queue",
        name = {"enabled", "consumer-enabled"},
        havingValue = "true")
class JudgeQueueConsumer {

    private final JudgeExecutionService judgeExecutionService;

    JudgeQueueConsumer(JudgeExecutionService judgeExecutionService) {
        this.judgeExecutionService = judgeExecutionService;
    }

    @RabbitListener(
            queues = "${aubb.judge.queue.queue-name:aubb.judge.jobs}",
            concurrency = "${aubb.judge.queue.concurrency:4}",
            containerFactory = "judgeQueueListenerContainerFactory")
    public void consume(String judgeJobIdText) {
        judgeExecutionService.executeJudgeJob(Long.valueOf(judgeJobIdText));
    }
}
