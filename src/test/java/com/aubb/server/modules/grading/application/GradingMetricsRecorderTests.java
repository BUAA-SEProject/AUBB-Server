package com.aubb.server.modules.grading.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.modules.grading.domain.appeal.GradeAppealStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GradingMetricsRecorderTests {

    @Test
    void gradePublicationCounterSeparatesInitialAndRepublish() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GradingMetricsRecorder recorder = new GradingMetricsRecorder(meterRegistry);

        recorder.recordGradePublication(true);
        recorder.recordGradePublication(false);

        assertThat(meterRegistry
                        .get(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC)
                        .tag("publish_type", "initial")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry
                        .get(GradingMetricsRecorder.GRADE_PUBLICATIONS_METRIC)
                        .tag("publish_type", "republish")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
    }

    @Test
    void appealCountersTrackCreatedAndReviewedResults() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GradingMetricsRecorder recorder = new GradingMetricsRecorder(meterRegistry);

        recorder.recordAppealCreated();
        recorder.recordAppealReviewed(GradeAppealStatus.ACCEPTED);
        recorder.recordAppealReviewed(GradeAppealStatus.REJECTED);

        assertThat(meterRegistry
                        .get(GradingMetricsRecorder.GRADE_APPEALS_CREATED_METRIC)
                        .counter()
                        .count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry
                        .get(GradingMetricsRecorder.GRADE_APPEALS_REVIEWED_METRIC)
                        .tag("result", "accepted")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry
                        .get(GradingMetricsRecorder.GRADE_APPEALS_REVIEWED_METRIC)
                        .tag("result", "rejected")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
    }
}
