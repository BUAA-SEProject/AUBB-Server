package com.aubb.server.modules.judge.application;

import com.aubb.server.config.JudgeQueueConfiguration.JudgeQueueProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JudgeMetricsRecorder {

    public static final String JUDGE_QUEUE_DEPTH_METRIC = "aubb.judge.queue.depth";
    public static final String JUDGE_JOB_EXECUTIONS_METRIC = "aubb.judge.job.executions";
    public static final String JUDGE_JOB_EXECUTION_DURATION_METRIC = "aubb.judge.job.execution";

    private final AmqpAdmin amqpAdmin;
    private final JudgeQueueProperties judgeQueueProperties;
    private final Counter judgeJobSucceededCounter;
    private final Counter judgeJobFailedCounter;
    private final Timer judgeJobSucceededTimer;
    private final Timer judgeJobFailedTimer;

    public JudgeMetricsRecorder(
            MeterRegistry meterRegistry, AmqpAdmin amqpAdmin, JudgeQueueProperties judgeQueueProperties) {
        this.amqpAdmin = amqpAdmin;
        this.judgeQueueProperties = judgeQueueProperties;
        Gauge.builder(JUDGE_QUEUE_DEPTH_METRIC, this, JudgeMetricsRecorder::measureQueueDepth)
                .description("当前 judge 执行队列中的待消费任务数")
                .strongReference(true)
                .register(meterRegistry);
        this.judgeJobSucceededCounter = Counter.builder(JUDGE_JOB_EXECUTIONS_METRIC)
                .description("judge 任务执行次数，按结果区分")
                .tag("result", "succeeded")
                .register(meterRegistry);
        this.judgeJobFailedCounter = Counter.builder(JUDGE_JOB_EXECUTIONS_METRIC)
                .description("judge 任务执行次数，按结果区分")
                .tag("result", "failed")
                .register(meterRegistry);
        this.judgeJobSucceededTimer = Timer.builder(JUDGE_JOB_EXECUTION_DURATION_METRIC)
                .description("judge 任务执行耗时")
                .tag("result", "succeeded")
                .register(meterRegistry);
        this.judgeJobFailedTimer = Timer.builder(JUDGE_JOB_EXECUTION_DURATION_METRIC)
                .description("judge 任务执行耗时")
                .tag("result", "failed")
                .register(meterRegistry);
    }

    public void recordExecution(Duration duration, boolean failed) {
        Duration safeDuration = duration == null || duration.isNegative() ? Duration.ZERO : duration;
        if (failed) {
            judgeJobFailedCounter.increment();
            judgeJobFailedTimer.record(safeDuration);
            return;
        }
        judgeJobSucceededCounter.increment();
        judgeJobSucceededTimer.record(safeDuration);
    }

    double measureQueueDepth() {
        if (!judgeQueueProperties.enabled()) {
            return Double.NaN;
        }
        try {
            Properties queueProperties = amqpAdmin.getQueueProperties(judgeQueueProperties.queueName());
            if (queueProperties == null) {
                return Double.NaN;
            }
            Object messageCount = queueProperties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            if (messageCount instanceof Number number) {
                return number.doubleValue();
            }
            return Double.NaN;
        } catch (AmqpException exception) {
            log.debug(
                    "Failed to fetch judge queue depth, queueName={}, error={}",
                    judgeQueueProperties.queueName(),
                    exception.getMessage());
            return Double.NaN;
        }
    }
}
