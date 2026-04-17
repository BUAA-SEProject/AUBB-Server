package com.aubb.server.common.redis;

import com.aubb.server.config.RedisEnhancementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class RedisEnhancementHealthIndicator implements HealthIndicator {

    private final RedisEnhancementProperties properties;
    private final org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final RedisAvailabilityTracker availabilityTracker;

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("mode", "disabled")
                    .build();
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            return Health.unknown()
                    .withDetail("enabled", true)
                    .withDetail("connected", false)
                    .withDetail("mode", "degraded")
                    .withDetail("reason", "connection_factory_missing")
                    .build();
        }
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            availabilityTracker.recordSuccess();
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("connected", StringUtils.hasText(pong))
                    .withDetail("response", pong)
                    .withDetail("host", properties.getHost())
                    .withDetail("port", properties.getPort())
                    .build();
        } catch (Exception exception) {
            availabilityTracker.recordFailure("health", exception);
            return Health.unknown()
                    .withDetail("enabled", true)
                    .withDetail("connected", false)
                    .withDetail("mode", "degraded")
                    .withDetail("host", properties.getHost())
                    .withDetail("port", properties.getPort())
                    .withException(exception)
                    .build();
        }
    }
}
