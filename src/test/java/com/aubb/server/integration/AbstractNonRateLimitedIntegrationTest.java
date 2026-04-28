package com.aubb.server.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class AbstractNonRateLimitedIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void disableRateLimit(DynamicPropertyRegistry registry) {
        registry.add("aubb.redis.rate-limit.enabled", () -> "false");
    }
}
