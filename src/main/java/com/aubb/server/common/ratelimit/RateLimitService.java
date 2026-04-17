package com.aubb.server.common.ratelimit;

public interface RateLimitService {

    RateLimitDecision check(RateLimitRequest request);

    default void assertAllowed(RateLimitRequest request) {
        RateLimitDecision decision = check(request);
        if (!decision.permitted()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
