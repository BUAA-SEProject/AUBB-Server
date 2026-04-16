package com.aubb.server.modules.judge.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "aubb.judge.queue", name = "enabled", havingValue = "false", matchIfMissing = true)
class JudgeExecutionLocalListener {

    private final JudgeExecutionService judgeExecutionService;

    JudgeExecutionLocalListener(JudgeExecutionService judgeExecutionService) {
        this.judgeExecutionService = judgeExecutionService;
    }

    @Async("judgeExecutionTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJudgeExecutionRequested(JudgeExecutionRequestedEvent event) {
        judgeExecutionService.executeJudgeJob(event.judgeJobId());
    }
}
