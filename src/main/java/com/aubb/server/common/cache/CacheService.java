package com.aubb.server.common.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface CacheService {

    <T> T getOrLoad(String cacheName, String keySuffix, Duration ttl, Class<T> valueType, Supplier<T> loader);

    <T> List<T> getOrLoadList(
            String cacheName, String keySuffix, Duration ttl, Class<T> elementType, Supplier<List<T>> loader);

    void evict(String cacheName, String keySuffix);

    default void evictAll(String cacheName, Collection<String> keySuffixes) {
        if (keySuffixes == null) {
            return;
        }
        keySuffixes.stream().distinct().forEach(keySuffix -> evict(cacheName, keySuffix));
    }
}
