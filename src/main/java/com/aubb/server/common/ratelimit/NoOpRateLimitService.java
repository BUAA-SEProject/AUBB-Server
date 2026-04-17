package com.aubb.server.common.ratelimit;

public class NoOpRateLimitService implements RateLimitService {

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        return RateLimitDecision.allowed();
    }
}
