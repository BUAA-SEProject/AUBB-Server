package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.LongSupplier;

final class IntegrationTestAwait {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private IntegrationTestAwait() {}

    static void awaitCount(LongSupplier actualSupplier, long expected) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (actualSupplier.getAsLong() == expected) {
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待异步结果时被中断", exception);
            }
        }
        assertThat(actualSupplier.getAsLong()).isEqualTo(expected);
    }
}
