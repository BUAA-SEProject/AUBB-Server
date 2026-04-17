package com.aubb.server.common.ratelimit;

public record RateLimitDecision(boolean permitted, long retryAfterSeconds) {

    public static RateLimitDecision allowed() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision rejected(long retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds);
    }
}
