package com.aubb.server.common.redis;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;

public class RedisEnhancementMetrics {

    private static final String CACHE_OPERATIONS_METRIC = "aubb_cache_operations_total";
    private static final String RATE_LIMIT_DECISIONS_METRIC = "aubb_rate_limit_decisions_total";
    private static final String REDIS_AVAILABLE_METRIC = "aubb_redis_available";
    private static final String REDIS_ENABLED_METRIC = "aubb_redis_enabled";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger available = new AtomicInteger(0);
    private final AtomicInteger enabled = new AtomicInteger(0);

    public RedisEnhancementMetrics(MeterRegistry meterRegistry, boolean redisEnabled) {
        this.meterRegistry = meterRegistry;
        enabled.set(redisEnabled ? 1 : 0);
        Gauge.builder(REDIS_AVAILABLE_METRIC, available, AtomicInteger::get)
                .description("Redis 增强链路当前是否可用，1 表示可用，0 表示不可用")
                .register(meterRegistry);
        Gauge.builder(REDIS_ENABLED_METRIC, enabled, AtomicInteger::get)
                .description("Redis 增强链路当前是否启用，1 表示启用，0 表示关闭")
                .register(meterRegistry);
    }

    public void recordCacheOperation(String cacheName, String operation, String result) {
        meterRegistry
                .counter(CACHE_OPERATIONS_METRIC, "cache", cacheName, "operation", operation, "result", result)
                .increment();
    }

    public void recordRateLimitDecision(String policy, String result) {
        meterRegistry
                .counter(RATE_LIMIT_DECISIONS_METRIC, "policy", policy, "result", result)
                .increment();
    }

    public void setAvailable(boolean redisAvailable) {
        available.set(redisAvailable ? 1 : 0);
    }
}
