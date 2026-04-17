package com.aubb.server.common.redis;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisAvailabilityTracker {

    private static final Duration FAILURE_LOG_COOLDOWN = Duration.ofSeconds(60);

    private final AtomicBoolean available = new AtomicBoolean(false);
    private final AtomicLong lastFailureLogAt = new AtomicLong(0);
    private final RedisEnhancementMetrics metrics;

    public RedisAvailabilityTracker(RedisEnhancementMetrics metrics) {
        this.metrics = metrics;
    }

    public void recordSuccess() {
        boolean changed = available.getAndSet(true) == false;
        metrics.setAvailable(true);
        if (changed) {
            log.info("Redis 增强链路恢复可用");
        }
    }

    public void recordFailure(String operation, Exception exception) {
        metrics.setAvailable(false);
        available.set(false);
        long now = System.currentTimeMillis();
        long previous = lastFailureLogAt.get();
        if (now - previous < FAILURE_LOG_COOLDOWN.toMillis() && previous != 0) {
            return;
        }
        if (lastFailureLogAt.compareAndSet(previous, now)) {
            log.warn(
                    "Redis 增强链路暂时不可用 operation={} errorType={} message={}",
                    operation,
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available.get();
    }
}
