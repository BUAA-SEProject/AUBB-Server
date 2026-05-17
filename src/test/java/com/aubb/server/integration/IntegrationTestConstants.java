package com.aubb.server.integration;

/**
 * 集成测试共享常量。
 * <p>
 * 消除各测试类中重复的 MinIO、Redis、JWT 等硬编码凭据。
 */
public final class IntegrationTestConstants {

    private IntegrationTestConstants() {}

    // ── JWT ────────────────────────────────────────────────────────────
    public static final String JWT_SECRET = "test-jwt-secret-for-aubb-server-0123456789";

    // ── MinIO ──────────────────────────────────────────────────────────
    public static final String MINIO_ACCESS_KEY = "aubbminio";
    public static final String MINIO_SECRET_KEY = "aubbminio-secret";

    // ── Redis ──────────────────────────────────────────────────────────
    public static final String REDIS_PASSWORD = "aubb-redis-secret";
}
