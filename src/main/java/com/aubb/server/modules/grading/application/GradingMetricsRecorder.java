package com.aubb.server.modules.grading.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class GradingMetricsRecorder {

    public static final String GRADE_PUBLICATIONS_METRIC = "aubb.grading.grade.publications";

    private final Counter initialPublicationCounter;
    private final Counter republishCounter;

    public GradingMetricsRecorder(MeterRegistry meterRegistry) {
        this.initialPublicationCounter = Counter.builder(GRADE_PUBLICATIONS_METRIC)
                .description("成绩发布次数，区分首发与重复发布")
                .tag("publish_type", "initial")
                .register(meterRegistry);
        this.republishCounter = Counter.builder(GRADE_PUBLICATIONS_METRIC)
                .description("成绩发布次数，区分首发与重复发布")
                .tag("publish_type", "republish")
                .register(meterRegistry);
    }

    public void recordGradePublication(boolean initialPublication) {
        recordAfterCommit(() -> {
            if (initialPublication) {
                initialPublicationCounter.increment();
                return;
            }
            republishCounter.increment();
        });
    }

    private void recordAfterCommit(Runnable recorder) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recorder.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recorder.run();
            }
        });
    }
}
