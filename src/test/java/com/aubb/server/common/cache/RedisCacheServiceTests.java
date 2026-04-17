package com.aubb.server.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SimpleMeterRegistry meterRegistry;
    private RedisAvailabilityTracker availabilityTracker;
    private RedisCacheService cacheService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        RedisEnhancementProperties properties = new RedisEnhancementProperties();
        properties.setEnabled(true);
        properties.setEnvironment("test");
        RedisKeyFactory redisKeyFactory = new RedisKeyFactory(properties);
        RedisEnhancementMetrics metrics = new RedisEnhancementMetrics(meterRegistry, true);
        availabilityTracker = new RedisAvailabilityTracker(metrics);
        cacheService =
                new RedisCacheService(redisTemplate, new ObjectMapper(), redisKeyFactory, metrics, availabilityTracker);
    }

    @Test
    void getOrLoadReturnsCachedValueOnHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("aubb:test:cache:notificationunreadcount:user:1"))
                .thenReturn("{\"unreadCount\":2}");
        AtomicInteger loads = new AtomicInteger();

        TestUnreadCount result = cacheService.getOrLoad(
                "notificationUnreadCount", "user:1", Duration.ofMinutes(1), TestUnreadCount.class, () -> {
                    loads.incrementAndGet();
                    return new TestUnreadCount(99);
                });

        assertThat(result.unreadCount()).isEqualTo(2);
        assertThat(loads).hasValue(0);
        assertThat(counterValue("notificationUnreadCount", "get", "hit")).isEqualTo(1.0);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getOrLoadLoadsAndStoresValueOnMiss() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("aubb:test:cache:mycoursessummary:user:7")).thenReturn(null);

        TestUnreadCount result = cacheService.getOrLoad(
                "myCoursesSummary",
                "user:7",
                Duration.ofMinutes(2),
                TestUnreadCount.class,
                () -> new TestUnreadCount(5));

        assertThat(result.unreadCount()).isEqualTo(5);
        assertThat(counterValue("myCoursesSummary", "get", "miss")).isEqualTo(1.0);
        assertThat(counterValue("myCoursesSummary", "put", "success")).isEqualTo(1.0);
        verify(valueOperations)
                .set(
                        eq("aubb:test:cache:mycoursessummary:user:7"),
                        eq(new ObjectMapper().writeValueAsString(new TestUnreadCount(5))),
                        eq(Duration.ofMinutes(2)));
    }

    @Test
    void getOrLoadFallsBackToLoaderWhenRedisReadFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        TestUnreadCount result = cacheService.getOrLoad(
                "notificationUnreadCount",
                "user:9",
                Duration.ofMinutes(1),
                TestUnreadCount.class,
                () -> new TestUnreadCount(8));

        assertThat(result.unreadCount()).isEqualTo(8);
        assertThat(availabilityTracker.isAvailable()).isFalse();
        assertThat(counterValue("notificationUnreadCount", "get", "error")).isEqualTo(1.0);
    }

    @Test
    void evictRecordsErrorAndDoesNotThrowWhenRedisFails() {
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete(anyString());

        assertThatCode(() -> cacheService.evict("notificationUnreadCount", "user:1"))
                .doesNotThrowAnyException();

        assertThat(counterValue("notificationUnreadCount", "evict", "error")).isEqualTo(1.0);
        assertThat(availabilityTracker.isAvailable()).isFalse();
    }

    private double counterValue(String cacheName, String operation, String result) {
        return meterRegistry
                .find("aubb_cache_operations_total")
                .tags("cache", cacheName, "operation", operation, "result", result)
                .counter()
                .count();
    }

    private record TestUnreadCount(int unreadCount) {}
}
