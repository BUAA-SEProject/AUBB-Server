package com.aubb.server.modules.grading.application;

import static org.assertj.core.api.Assertions.assertThat;

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
}
