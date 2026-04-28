package com.aubb.server.common.ratelimit;

import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRateLimitService implements RateLimitService {

    private static final long CLEANUP_INTERVAL = 256L;

    private final RedisEnhancementProperties properties;
    private final RedisKeyFactory redisKeyFactory;
    private final RedisEnhancementMetrics metrics;
    private final String resultPrefix;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    public InMemoryRateLimitService(
            RedisEnhancementProperties properties, RedisKeyFactory redisKeyFactory, RedisEnhancementMetrics metrics) {
        this(properties, redisKeyFactory, metrics, "");
    }

    public InMemoryRateLimitService(
            RedisEnhancementProperties properties,
            RedisKeyFactory redisKeyFactory,
            RedisEnhancementMetrics metrics,
            String resultPrefix) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.redisKeyFactory = Objects.requireNonNull(redisKeyFactory, "redisKeyFactory");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.resultPrefix = resultPrefix == null ? "" : resultPrefix;
    }

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        RedisEnhancementProperties.Policy policy = RateLimitRequestSupport.resolvePolicy(properties, request);
        if (RateLimitRequestSupport.isPolicyDisabled(policy)) {
            recordDecision(request, "allowed");
            return RateLimitDecision.allowed();
        }

        long nowMillis = Instant.now().toEpochMilli();
        long windowMillis = Math.max(policy.getWindow().toMillis(), 1_000L);
        long windowStartMillis = nowMillis - Math.floorMod(nowMillis, windowMillis);
        String key = request.policy() + ":" + RateLimitRequestSupport.buildKeySuffix(request, redisKeyFactory);
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAtMillis() <= nowMillis) {
                return new WindowCounter(windowStartMillis, windowStartMillis + windowMillis, 1);
            }
            return new WindowCounter(existing.windowStartMillis(), existing.expiresAtMillis(), existing.count() + 1);
        });

        maybeCleanup(nowMillis);
        if (counter.count() > policy.getLimit()) {
            recordDecision(request, "rejected");
            return RateLimitDecision.rejected(retryAfterSeconds(counter.expiresAtMillis(), nowMillis));
        }

        recordDecision(request, "allowed");
        return RateLimitDecision.allowed();
    }

    private void maybeCleanup(long nowMillis) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= nowMillis);
    }

    private long retryAfterSeconds(long expiresAtMillis, long nowMillis) {
        long remainingMillis = Math.max(expiresAtMillis - nowMillis, 0L);
        return Math.max(Duration.ofMillis(remainingMillis).toSeconds(), 1L);
    }

    private void recordDecision(RateLimitRequest request, String result) {
        if (request != null && request.policy() != null) {
            metrics.recordRateLimitDecision(request.policy(), resultPrefix + result);
        }
    }

    private record WindowCounter(long windowStartMillis, long expiresAtMillis, int count) {}
}
