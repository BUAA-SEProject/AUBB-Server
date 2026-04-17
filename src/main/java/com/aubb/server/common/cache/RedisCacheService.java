package com.aubb.server.common.cache;

import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class RedisCacheService implements CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisKeyFactory redisKeyFactory;
    private final RedisEnhancementMetrics metrics;
    private final RedisAvailabilityTracker availabilityTracker;

    @Override
    public <T> T getOrLoad(String cacheName, String keySuffix, Duration ttl, Class<T> valueType, Supplier<T> loader) {
        return getOrLoadInternal(cacheName, keySuffix, ttl, objectMapper.constructType(valueType), loader);
    }

    @Override
    public <T> List<T> getOrLoadList(
            String cacheName, String keySuffix, Duration ttl, Class<T> elementType, Supplier<List<T>> loader) {
        JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return getOrLoadInternal(cacheName, keySuffix, ttl, javaType, loader);
    }

    @Override
    public void evict(String cacheName, String keySuffix) {
        String key = redisKeyFactory.cacheKey(cacheName, keySuffix);
        try {
            redisTemplate.delete(key);
            availabilityTracker.recordSuccess();
            metrics.recordCacheOperation(cacheName, "evict", "success");
        } catch (Exception exception) {
            availabilityTracker.recordFailure("cache.evict", exception);
            metrics.recordCacheOperation(cacheName, "evict", "error");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrLoadInternal(
            String cacheName, String keySuffix, Duration ttl, JavaType javaType, Supplier<T> loader) {
        String key = redisKeyFactory.cacheKey(cacheName, keySuffix);
        try {
            String payload = redisTemplate.opsForValue().get(key);
            availabilityTracker.recordSuccess();
            if (payload != null) {
                metrics.recordCacheOperation(cacheName, "get", "hit");
                return (T) objectMapper.readValue(payload, javaType);
            }
            metrics.recordCacheOperation(cacheName, "get", "miss");
        } catch (Exception exception) {
            availabilityTracker.recordFailure("cache.get", exception);
            metrics.recordCacheOperation(cacheName, "get", "error");
            return loader.get();
        }

        T loaded = loader.get();
        if (loaded == null) {
            return null;
        }
        try {
            redisTemplate
                    .opsForValue()
                    .set(key, objectMapper.writeValueAsString(loaded), ttl == null ? Duration.ZERO : ttl);
            availabilityTracker.recordSuccess();
            metrics.recordCacheOperation(cacheName, "put", "success");
        } catch (Exception exception) {
            availabilityTracker.recordFailure("cache.put", exception);
            metrics.recordCacheOperation(cacheName, "put", "error");
        }
        return loaded;
    }
}
