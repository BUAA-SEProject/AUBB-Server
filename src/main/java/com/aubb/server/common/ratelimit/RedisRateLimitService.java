package com.aubb.server.common.ratelimit;

import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

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
        if (request == null || !StringUtils.hasText(request.policy())) {
            return RateLimitDecision.allowed();
        }
        RedisEnhancementProperties.Policy policy =
                properties.getRateLimit().getPolicies().get(request.policy());
        if (policy == null
                || policy.getLimit() <= 0
                || policy.getWindow() == null
                || policy.getWindow().isZero()) {
            metrics.recordRateLimitDecision(request.policy(), "allowed");
            return RateLimitDecision.allowed();
        }
        String keySuffix = buildKeySuffix(request);
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
            metrics.recordRateLimitDecision(request.policy(), "fallback");
            return RateLimitDecision.allowed();
        }
    }

    private String buildKeySuffix(RateLimitRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request.userId() != null) {
            builder.append("user").append(':').append(request.userId());
        }
        if (StringUtils.hasText(request.clientIp())) {
            appendSegment(builder, "ip:" + redisKeyFactory.sanitize(request.clientIp()));
        }
        if (StringUtils.hasText(request.subjectKey())) {
            appendSegment(builder, redisKeyFactory.sanitize(request.subjectKey().toLowerCase(Locale.ROOT)));
        }
        if (builder.isEmpty()) {
            builder.append("global");
        }
        return builder.toString();
    }

    private void appendSegment(StringBuilder builder, String segment) {
        if (!builder.isEmpty()) {
            builder.append(':');
        }
        builder.append(segment);
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
