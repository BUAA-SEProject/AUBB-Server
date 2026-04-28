package com.aubb.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryRateLimitServiceTests {

    private SimpleMeterRegistry meterRegistry;
    private InMemoryRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        RedisEnhancementProperties properties = new RedisEnhancementProperties();
        RedisEnhancementProperties.Policy loginPolicy = new RedisEnhancementProperties.Policy();
        loginPolicy.setLimit(1);
        loginPolicy.setWindow(Duration.ofMinutes(1));
        properties.getRateLimit().getPolicies().put("login", loginPolicy);
        rateLimitService = new InMemoryRateLimitService(
                properties, new RedisKeyFactory(properties), new RedisEnhancementMetrics(meterRegistry, false));
    }

    @Test
    void rejectsSecondRequestWithinSameWindow() {
        RateLimitDecision first =
                rateLimitService.check(new RateLimitRequest("login", 7L, "127.0.0.1", "school-admin"));
        RateLimitDecision second =
                rateLimitService.check(new RateLimitRequest("login", 7L, "127.0.0.1", "school-admin"));

        assertThat(first.permitted()).isTrue();
        assertThat(second.permitted()).isFalse();
        assertThat(second.retryAfterSeconds()).isPositive();
        assertThat(counterValue("login", "allowed")).isEqualTo(1.0);
        assertThat(counterValue("login", "rejected")).isEqualTo(1.0);
    }

    @Test
    void usesForwardedIpAndSubjectToIsolateDifferentBuckets() {
        RateLimitDecision first =
                rateLimitService.check(new RateLimitRequest("login", null, "203.0.113.10", "school-admin"));
        RateLimitDecision second =
                rateLimitService.check(new RateLimitRequest("login", null, "203.0.113.11", "school-admin"));

        assertThat(first.permitted()).isTrue();
        assertThat(second.permitted()).isTrue();
        assertThat(counterValue("login", "allowed")).isEqualTo(2.0);
    }

    private double counterValue(String policy, String result) {
        return meterRegistry
                .find("aubb_rate_limit_decisions_total")
                .tags("policy", policy, "result", result)
                .counter()
                .count();
    }
}
