package com.aubb.server.common.redis;

import com.aubb.server.config.RedisEnhancementProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class RedisKeyFactory {

    private final String prefix;

    public RedisKeyFactory(RedisEnhancementProperties properties) {
        this.prefix = "%s:%s".formatted(sanitize(properties.getNamespace()), sanitize(properties.getEnvironment()));
    }

    public String cacheKey(String cacheName, String keySuffix) {
        return join(prefix, "cache", cacheName, keySuffix);
    }

    public String rateLimitKey(String policy, String keySuffix) {
        return join(prefix, "ratelimit", policy, keySuffix);
    }

    public String realtimeChannel(String topic) {
        return join(prefix, "realtime", topic);
    }

    public String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9:_-]", "_");
    }

    public String hash(String value) {
        if (!StringUtils.hasText(value)) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(this::sanitize)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + ":" + right)
                .orElse(prefix);
    }
}
