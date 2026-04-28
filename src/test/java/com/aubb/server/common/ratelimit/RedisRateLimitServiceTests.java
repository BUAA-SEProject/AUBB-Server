package com.aubb.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    private SimpleMeterRegistry meterRegistry;
    private RedisAvailabilityTracker availabilityTracker;
    private RedisRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        RedisEnhancementProperties properties = new RedisEnhancementProperties();
        properties.setEnabled(true);
        properties.setEnvironment("test");
        RedisEnhancementProperties.Policy loginPolicy = new RedisEnhancementProperties.Policy();
        loginPolicy.setLimit(1);
        loginPolicy.setWindow(Duration.ofMinutes(1));
        properties.getRateLimit().getPolicies().put("login", loginPolicy);
        RedisKeyFactory redisKeyFactory = new RedisKeyFactory(properties);
        RedisEnhancementMetrics metrics = new RedisEnhancementMetrics(meterRegistry, true);
        availabilityTracker = new RedisAvailabilityTracker(metrics);
        rateLimitService =
                new RedisRateLimitService(redisTemplate, properties, redisKeyFactory, metrics, availabilityTracker);
    }

    @Test
    void allowsRequestWithinConfiguredWindow() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(List.of(1L, 60L));

        RateLimitDecision decision =
                rateLimitService.check(new RateLimitRequest("login", 7L, "127.0.0.1", "school-admin"));

        assertThat(decision.permitted()).isTrue();
        assertThat(decision.retryAfterSeconds()).isZero();
        assertThat(counterValue("login", "allowed")).isEqualTo(1.0);
        assertThat(availabilityTracker.isAvailable()).isTrue();
    }

    @Test
    void rejectsRequestAfterLimitExceeded() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(List.of(2L, 45L));

        RateLimitDecision decision =
                rateLimitService.check(new RateLimitRequest("login", 7L, "127.0.0.1", "school-admin"));

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(45L);
        assertThat(counterValue("login", "rejected")).isEqualTo(1.0);
    }

    @Test
    void marksBackendUnavailableWhenRedisTemporarilyUnavailable() {
        when(redisTemplate.execute(any(), anyList(), any())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> rateLimitService.check(new RateLimitRequest("login", 7L, "127.0.0.1", "school-admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis rate limit backend unavailable");
        assertThat(availabilityTracker.isAvailable()).isFalse();
    }

    private double counterValue(String policy, String result) {
        return meterRegistry
                .find("aubb_rate_limit_decisions_total")
                .tags("policy", policy, "result", result)
                .counter()
                .count();
    }
}
