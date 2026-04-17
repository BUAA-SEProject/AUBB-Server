package com.aubb.server.common.cache;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public class NoOpCacheService implements CacheService {

    @Override
    public <T> T getOrLoad(String cacheName, String keySuffix, Duration ttl, Class<T> valueType, Supplier<T> loader) {
        return loader.get();
    }

    @Override
    public <T> List<T> getOrLoadList(
            String cacheName, String keySuffix, Duration ttl, Class<T> elementType, Supplier<List<T>> loader) {
        return loader.get();
    }

    @Override
    public void evict(String cacheName, String keySuffix) {
        // Redis 关闭时不做任何事情。
    }
}
