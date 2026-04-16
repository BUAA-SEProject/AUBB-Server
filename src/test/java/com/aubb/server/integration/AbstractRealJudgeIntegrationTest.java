package com.aubb.server.integration;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
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
            FROM debian:bookworm-slim

            RUN apt-get update \\
                && apt-get install -y --no-install-recommends python3 ca-certificates g++ \\
                && rm -rf /var/lib/apt/lists/*

            WORKDIR /opt

            COPY --from=go-judge /opt/go-judge /opt/
            COPY --from=java21 /opt/java/openjdk /opt/java/openjdk
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
}
