package com.aubb.server.modules.grading.application;

import com.aubb.server.modules.grading.domain.appeal.GradeAppealStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class GradingMetricsRecorder {

    public static final String GRADE_PUBLICATIONS_METRIC = "aubb.grading.grade.publications";
    public static final String GRADE_APPEALS_CREATED_METRIC = "aubb.grading.appeal.creations";
    public static final String GRADE_APPEALS_REVIEWED_METRIC = "aubb.grading.appeal.reviews";

    private final Counter initialPublicationCounter;
    private final Counter republishCounter;
    private final Counter appealCreatedCounter;
    private final Map<GradeAppealStatus, Counter> appealReviewedCounters;

    public GradingMetricsRecorder(MeterRegistry meterRegistry) {
        this.initialPublicationCounter = Counter.builder(GRADE_PUBLICATIONS_METRIC)
                .description("成绩发布次数，区分首发与重复发布")
                .tag("publish_type", "initial")
                .register(meterRegistry);
        this.republishCounter = Counter.builder(GRADE_PUBLICATIONS_METRIC)
                .description("成绩发布次数，区分首发与重复发布")
                .tag("publish_type", "republish")
                .register(meterRegistry);
        this.appealCreatedCounter = Counter.builder(GRADE_APPEALS_CREATED_METRIC)
                .description("学生发起的成绩申诉数量")
                .register(meterRegistry);
        this.appealReviewedCounters = new EnumMap<>(GradeAppealStatus.class);
        for (GradeAppealStatus status : GradeAppealStatus.values()) {
            appealReviewedCounters.put(
                    status,
                    Counter.builder(GRADE_APPEALS_REVIEWED_METRIC)
                            .description("成绩申诉处理结果数量")
                            .tag("result", status.name().toLowerCase(Locale.ROOT))
                            .register(meterRegistry));
        }
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

    public void recordAppealCreated() {
        recordAfterCommit(appealCreatedCounter::increment);
    }

    public void recordAppealReviewed(GradeAppealStatus status) {
        recordAfterCommit(() -> appealReviewedCounters.get(status).increment());
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
