package com.aubb.server.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

final class RedisIntegrationTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");
    private static final String REDIS_PASSWORD = "aubb-redis-secret";

    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(REDIS_IMAGE)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD, "--appendonly", "no")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    static {
        Startables.deepStart(REDIS_CONTAINER).join();
    }

    private RedisIntegrationTestSupport() {}

    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("aubb.redis.enabled", () -> "true");
        registry.add("aubb.redis.host", REDIS_CONTAINER::getHost);
        registry.add("aubb.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
        registry.add("aubb.redis.password", () -> REDIS_PASSWORD);
    }

    static void flushAll() {
        try {
            REDIS_CONTAINER.execInContainer("redis-cli", "-a", REDIS_PASSWORD, "FLUSHALL");
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 Redis 测试数据", exception);
        }
    }
}
