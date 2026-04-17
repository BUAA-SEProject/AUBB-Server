package com.aubb.server.common.ratelimit;

public record RateLimitRequest(String policy, Long userId, String clientIp, String subjectKey) {}
