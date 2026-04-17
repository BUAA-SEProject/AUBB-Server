package com.aubb.server.integration;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractRealJudgeIntegrationTest {

    protected static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    protected static final DateTimeFormatter OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final long JUDGE_CLEANUP_TIMEOUT_MS = 15_000L;

    private static final DockerImageName MINIO_IMAGE =
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z");
    private static final DockerImageName RABBITMQ_IMAGE = DockerImageName.parse("rabbitmq:4.1.0-management");
    private static final String MINIO_ACCESS_KEY = "aubbminio";
    private static final String MINIO_SECRET_KEY = "aubbminio-secret";
    private static final String MINIO_BUCKET = "aubb-real-go-judge-test";
    private static final String JUDGE_QUEUE_NAME = "aubb.judge.jobs.test";
    private static final String GO_JUDGE_TEST_DOCKERFILE = """
            FROM criyle/go-judge:v1.11.4 AS go-judge
            FROM eclipse-temurin:21.0.8_9-jdk-jammy AS java21
            FROM golang:1.22.12-bookworm AS go122
            FROM debian:bookworm-slim

            RUN apt-get update \\
                && apt-get install -y --no-install-recommends python3 ca-certificates g++ \\
                && rm -rf /var/lib/apt/lists/*

            WORKDIR /opt

            COPY --from=go-judge /opt/go-judge /opt/
            COPY --from=java21 /opt/java/openjdk /opt/java/openjdk
            COPY --from=go122 /usr/local/go /usr/local/go
            COPY mount.yaml /opt/mount.yaml

            EXPOSE 5050/tcp

            ENTRYPOINT ["./go-judge"]
            """;
    private static final ImageFromDockerfile GO_JUDGE_IMAGE = new ImageFromDockerfile("aubb-go-judge-test", false)
            .withFileFromString("Dockerfile", GO_JUDGE_TEST_DOCKERFILE)
            .withDockerfilePath("Dockerfile")
            .withFileFromPath("mount.yaml", Path.of("docker/go-judge/mount.yaml"));

    protected static final GenericContainer<?> GO_JUDGE_CONTAINER = new GenericContainer<>(GO_JUDGE_IMAGE)
            .withExposedPorts(5050)
            .withSharedMemorySize(256L * 1024L * 1024L)
            .withPrivilegedMode(true)
            .waitingFor(Wait.forHttp("/version").forPort(5050).forStatusCode(200));

    protected static final GenericContainer<?> MINIO_CONTAINER = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    protected static final RabbitMQContainer RABBITMQ_CONTAINER = new RabbitMQContainer(RABBITMQ_IMAGE);

    @Autowired(required = false)
    private AmqpAdmin amqpAdmin;

    static {
        Startables.deepStart(GO_JUDGE_CONTAINER, MINIO_CONTAINER, RABBITMQ_CONTAINER)
                .join();
    }

    @DynamicPropertySource
    static void registerRuntimeProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "aubb.judge.go-judge.base-url",
                () -> "http://" + GO_JUDGE_CONTAINER.getHost() + ":" + GO_JUDGE_CONTAINER.getMappedPort(5050));
        registry.add("aubb.judge.go-judge.enabled", () -> "true");
        registry.add("aubb.judge.queue.enabled", () -> "true");
        registry.add("aubb.judge.queue.queue-name", () -> JUDGE_QUEUE_NAME);
        registry.add("aubb.judge.queue.concurrency", () -> "2");
        registry.add("spring.rabbitmq.host", RABBITMQ_CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ_CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ_CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ_CONTAINER::getAdminPassword);
        registry.add("aubb.storage.minio.enabled", () -> "true");
        registry.add("aubb.storage.minio.auto-create-bucket", () -> "true");
        registry.add(
                "aubb.storage.minio.endpoint",
                () -> "http://" + MINIO_CONTAINER.getHost() + ":" + MINIO_CONTAINER.getMappedPort(9000));
        registry.add("aubb.storage.minio.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("aubb.storage.minio.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("aubb.storage.minio.bucket", () -> MINIO_BUCKET);
    }

    protected void resetJudgeTables(JdbcTemplate jdbcTemplate, String truncateSql) {
        purgeJudgeQueue();
        waitForRunningJudgeWorkToDrain(jdbcTemplate);
        executeTruncateWithRetry(jdbcTemplate, truncateSql);
    }

    private void purgeJudgeQueue() {
        if (amqpAdmin == null) {
            return;
        }
        try {
            amqpAdmin.purgeQueue(JUDGE_QUEUE_NAME, true);
        } catch (AmqpException ignored) {
            // 队列尚未声明或已被销毁时，测试清理直接回退到数据库侧等待即可。
        }
    }

    private void waitForRunningJudgeWorkToDrain(JdbcTemplate jdbcTemplate) {
        long deadline = System.currentTimeMillis() + JUDGE_CLEANUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Integer runningCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM judge_jobs WHERE status = 'RUNNING'", Integer.class);
            if (runningCount == null || runningCount == 0) {
                sleepSilently(150L);
                Integer recheckCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM judge_jobs WHERE status = 'RUNNING'", Integer.class);
                if (recheckCount == null || recheckCount == 0) {
                    return;
                }
            }
            sleepSilently(100L);
        }

        Integer pendingCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM judge_jobs WHERE status = 'PENDING'", Integer.class);
        Integer runningCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM judge_jobs WHERE status = 'RUNNING'", Integer.class);
        throw new AssertionError("judge 测试清理前仍存在进行中的评测任务，pending=%s, running=%s".formatted(pendingCount, runningCount));
    }

    private void executeTruncateWithRetry(JdbcTemplate jdbcTemplate, String truncateSql) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                jdbcTemplate.execute(truncateSql);
                return;
            } catch (DataAccessException exception) {
                if (!isDeadlock(exception) || attempt == 3) {
                    throw exception;
                }
                purgeJudgeQueue();
                waitForRunningJudgeWorkToDrain(jdbcTemplate);
                sleepSilently(150L);
            }
        }
    }

    private boolean isDeadlock(DataAccessException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("deadlock detected")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待 judge 测试清理完成时线程被中断", exception);
        }
    }
}
