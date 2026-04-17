package com.aubb.server.modules.judge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aubb.server.config.JudgeQueueConfiguration.JudgeQueueProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

class JudgeMetricsRecorderTests {

    @Test
    void queueDepthGaugeReadsRabbitQueuePropertiesWhenQueueEnabled() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        Properties queueProperties = new Properties();
        queueProperties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 7);
        when(amqpAdmin.getQueueProperties("aubb.judge.jobs")).thenReturn(queueProperties);

        new JudgeMetricsRecorder(
                meterRegistry,
                amqpAdmin,
                new JudgeQueueProperties(
                        true, true, true, "aubb.judge.jobs", "aubb.judge.jobs.dlq", "aubb.judge.jobs.dlx", "2", 8, 3));

        assertThat(meterRegistry
                        .get(JudgeMetricsRecorder.JUDGE_QUEUE_DEPTH_METRIC)
                        .gauge()
                        .value())
                .isEqualTo(7.0d);
    }

    @Test
    void executionMetricsTrackSucceededAndFailedRunsSeparately() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        JudgeMetricsRecorder recorder = new JudgeMetricsRecorder(
                meterRegistry,
                mock(AmqpAdmin.class),
                new JudgeQueueProperties(
                        false,
                        false,
                        false,
                        "aubb.judge.jobs",
                        "aubb.judge.jobs.dlq",
                        "aubb.judge.jobs.dlx",
                        "2",
                        8,
                        3));

        recorder.recordExecution(Duration.ofMillis(120), false);
        recorder.recordExecution(Duration.ofMillis(80), true);

        assertThat(meterRegistry
                        .get(JudgeMetricsRecorder.JUDGE_JOB_EXECUTIONS_METRIC)
                        .tag("result", "succeeded")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry
                        .get(JudgeMetricsRecorder.JUDGE_JOB_EXECUTIONS_METRIC)
                        .tag("result", "failed")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry
                        .get(JudgeMetricsRecorder.JUDGE_JOB_EXECUTION_DURATION_METRIC)
                        .tag("result", "succeeded")
                        .timer()
                        .count())
                .isEqualTo(1L);
        assertThat(meterRegistry
                        .get(JudgeMetricsRecorder.JUDGE_JOB_EXECUTION_DURATION_METRIC)
                        .tag("result", "failed")
                        .timer()
                        .count())
                .isEqualTo(1L);
    }
}
