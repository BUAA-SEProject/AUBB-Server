package com.aubb.server.common.ratelimit;

import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@RequiredArgsConstructor
public class RedisRateLimitService implements RateLimitService {

    private static final DefaultRedisScript<List> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return { current, ttl }
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisEnhancementProperties properties;
    private final RedisKeyFactory redisKeyFactory;
    private final RedisEnhancementMetrics metrics;
    private final RedisAvailabilityTracker availabilityTracker;

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        RedisEnhancementProperties.Policy policy = RateLimitRequestSupport.resolvePolicy(properties, request);
        if (RateLimitRequestSupport.isPolicyDisabled(policy)) {
            metrics.recordRateLimitDecision(request.policy(), "allowed");
            return RateLimitDecision.allowed();
        }
        String keySuffix = RateLimitRequestSupport.buildKeySuffix(request, redisKeyFactory);
        String key = redisKeyFactory.rateLimitKey(request.policy(), keySuffix);
        long windowSeconds = Math.max(policy.getWindow().getSeconds(), 1L);
        try {
            List<?> result = redisTemplate.execute(FIXED_WINDOW_SCRIPT, List.of(key), String.valueOf(windowSeconds));
            availabilityTracker.recordSuccess();
            long current = asLong(result, 0);
            long ttl = Math.max(asLong(result, 1), 0L);
            if (current > policy.getLimit()) {
                metrics.recordRateLimitDecision(request.policy(), "rejected");
                return RateLimitDecision.rejected(ttl);
            }
            metrics.recordRateLimitDecision(request.policy(), "allowed");
            return RateLimitDecision.allowed();
        } catch (Exception exception) {
            availabilityTracker.recordFailure("ratelimit.check", exception);
            throw new IllegalStateException("Redis rate limit backend unavailable", exception);
        }
    }

    private long asLong(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return 0;
        }
        Object value = result.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
