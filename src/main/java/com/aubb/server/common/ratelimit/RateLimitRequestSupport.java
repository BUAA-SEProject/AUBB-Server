package com.aubb.server.common.ratelimit;

import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.config.RedisEnhancementProperties;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class RateLimitRequestSupport {

    private RateLimitRequestSupport() {}

    static RedisEnhancementProperties.Policy resolvePolicy(
            RedisEnhancementProperties properties, RateLimitRequest request) {
        if (request == null || !StringUtils.hasText(request.policy())) {
            return null;
        }
        return properties.getRateLimit().getPolicies().get(request.policy());
    }

    static boolean isPolicyDisabled(RedisEnhancementProperties.Policy policy) {
        return policy == null
                || policy.getLimit() <= 0
                || policy.getWindow() == null
                || policy.getWindow().isZero();
    }

    static String buildKeySuffix(RateLimitRequest request, RedisKeyFactory redisKeyFactory) {
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

    private static void appendSegment(StringBuilder builder, String segment) {
        if (!builder.isEmpty()) {
            builder.append(':');
        }
        builder.append(segment);
    }
}
