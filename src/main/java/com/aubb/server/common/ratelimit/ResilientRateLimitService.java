package com.aubb.server.common.ratelimit;

import java.util.Objects;

public class ResilientRateLimitService implements RateLimitService {

    private final RateLimitService primary;
    private final RateLimitService fallback;

    public ResilientRateLimitService(RateLimitService primary, RateLimitService fallback) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        try {
            return primary.check(request);
        } catch (RuntimeException exception) {
            return fallback.check(request);
        }
    }
}
