package com.aubb.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ResilientRateLimitServiceTests {

    @Test
    void fallsBackToSecondaryDecisionWhenPrimaryThrows() {
        RateLimitService primary = mock(RateLimitService.class);
        RateLimitService fallback = mock(RateLimitService.class);
        when(primary.check(new RateLimitRequest("login", 7L, "127.0.0.1", "subject")))
                .thenThrow(new IllegalStateException("redis down"));
        when(fallback.check(new RateLimitRequest("login", 7L, "127.0.0.1", "subject")))
                .thenReturn(RateLimitDecision.rejected(42));

        ResilientRateLimitService service = new ResilientRateLimitService(primary, fallback);

        RateLimitDecision decision = service.check(new RateLimitRequest("login", 7L, "127.0.0.1", "subject"));

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(42L);
    }
}
