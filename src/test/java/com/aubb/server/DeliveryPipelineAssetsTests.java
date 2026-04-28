package com.aubb.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeliveryPipelineAssetsTests {

    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void deliveryAssetsExist() {
        assertTrue(Files.exists(ROOT.resolve("Dockerfile")), "Missing root Dockerfile");
        assertTrue(Files.exists(ROOT.resolve(".dockerignore")), "Missing .dockerignore");
        assertTrue(Files.exists(ROOT.resolve("deploy/compose.yaml")), "Missing deploy compose file");
        assertTrue(Files.exists(ROOT.resolve("deploy/.env.production.example")), "Missing deploy environment example");
        assertTrue(Files.exists(ROOT.resolve("deploy/.env.staging.example")), "Missing staging environment example");
        assertTrue(Files.exists(ROOT.resolve("deploy/.env.uat.example")), "Missing UAT environment example");
        assertTrue(Files.exists(ROOT.resolve(".github/workflows/ci.yml")), "Missing CI workflow");
        assertTrue(Files.exists(ROOT.resolve(".github/workflows/deploy.yml")), "Missing deploy workflow");
        assertTrue(Files.exists(ROOT.resolve("ops/judge/redrive-dlq.sh")), "Missing judge DLQ redrive script");
        assertTrue(Files.exists(ROOT.resolve("ops/judge/drain-queue.sh")), "Missing judge queue drain script");
        assertTrue(
                Files.exists(ROOT.resolve("ops/preflight/check-runtime-deps.sh")), "Missing runtime preflight script");
        assertTrue(Files.exists(ROOT.resolve("ops/openapi/export-static.sh")), "Missing static OpenAPI export script");
        assertTrue(Files.exists(ROOT.resolve("ops/release/backup-db.sh")), "Missing database backup script");
        assertTrue(Files.exists(ROOT.resolve("ops/release/restore-db.sh")), "Missing database restore script");
        assertTrue(Files.exists(ROOT.resolve("ops/release/release-drill.sh")), "Missing release drill script");
        assertTrue(Files.exists(ROOT.resolve("ops/release/rollback-release.sh")), "Missing rollback script");
        assertTrue(Files.exists(ROOT.resolve("monitoring/prometheus/prometheus.yml")), "Missing Prometheus config");
        assertTrue(Files.exists(ROOT.resolve("monitoring/prometheus/alerts.yml")), "Missing Prometheus alerts");
        assertTrue(
                Files.exists(ROOT.resolve("monitoring/grafana/dashboards/aubb-platform-overview.json")),
                "Missing Grafana dashboard");
        assertTrue(Files.exists(ROOT.resolve("monitoring/loki/loki-config.yml")), "Missing Loki config");
        assertTrue(Files.exists(ROOT.resolve("monitoring/promtail/promtail-config.yml")), "Missing Promtail config");
        assertTrue(
                Files.exists(ROOT.resolve("docs/operations/environments-and-release.md")),
                "Missing environments and release runbook");
        assertTrue(Files.exists(ROOT.resolve("docs/operations/observability.md")), "Missing observability runbook");
        assertTrue(
                Files.exists(ROOT.resolve("docs/operations/judge-reproducibility.md")),
                "Missing judge reproducibility runbook");
        assertTrue(Files.exists(ROOT.resolve("docs/redis.md")), "Missing Redis runbook");
    }

    @Test
    void rootComposeSupportsAppProfileWithoutLosingInfraMode() throws IOException {
        String compose = Files.readString(ROOT.resolve("compose.yaml"));

        assertTrue(compose.contains("profiles: ['app']"), "App service should be gated by compose profile");
        assertTrue(
                compose.contains("AUBB_JWT_SECRET=${AUBB_JWT_SECRET:-}"),
                "App compose should remain parseable without forcing JWT for infra-only mode");
        assertTrue(
                compose.contains("AUBB_JWT_SECRET is required for app profile"),
                "App compose should fail fast when the app profile starts without JWT");
        assertTrue(compose.contains("dockerfile: Dockerfile"), "App compose should build from root Dockerfile");
        assertTrue(compose.contains("\n  judge-worker:\n"), "Root compose should include dedicated judge worker");
        assertTrue(
                compose.contains("SPRING_MAIN_WEB_APPLICATION_TYPE=none"),
                "Judge worker should run without web server");
        assertTrue(
                compose.contains("AUBB_JUDGE_QUEUE_CONSUMER_ENABLED=true"),
                "Judge worker should explicitly enable queue consuming");
        assertTrue(
                compose.contains("AUBB_JUDGE_QUEUE_CONSUMER_ENABLED=false"),
                "Web app should disable embedded queue consumer when worker profile is used");
        assertTrue(compose.contains("\n  redis:\n"), "Root compose should expose Redis as optional enhancement infra");
        assertTrue(
                compose.contains("AUBB_REDIS_ENABLED=${AUBB_REDIS_ENABLED:-false}"),
                "App compose should support toggling Redis enhancement mode");
        assertTrue(!compose.contains("SPRING_DATA_REDIS_"), "Redis wiring should be removed from root compose");
    }

    @Test
    void deploymentComposeRequiresVersionedAppImage() throws IOException {
        String deployCompose = Files.readString(ROOT.resolve("deploy/compose.yaml"));

        assertTrue(deployCompose.contains("AUBB_APP_IMAGE:?"), "Deploy compose must require explicit image version");
        assertTrue(deployCompose.contains("SPRING_DATASOURCE_URL:?"), "Deploy compose must require datasource wiring");
        assertTrue(deployCompose.contains("AUBB_JWT_SECRET:?"), "Deploy compose must require JWT secret");
        assertTrue(
                deployCompose.contains("\n  judge-worker:\n"),
                "Deploy compose should support independent judge worker");
        assertTrue(
                deployCompose.contains("AUBB_JUDGE_QUEUE_CONSUMER_ENABLED"),
                "Deploy compose should explicitly wire judge worker queue consumer");
        assertTrue(
                deployCompose.contains("AUBB_REDIS_ENABLED"),
                "Deploy compose should support optional Redis enhancement envs");
        assertTrue(!deployCompose.contains("SPRING_DATA_REDIS_"), "Deploy compose should not use legacy Redis envs");
        assertTrue(
                !deployCompose.contains("AUBB_REDIS_REALTIME_ENABLED"),
                "Deploy compose should not carry unused Redis realtime switches");
        assertTrue(
                deployCompose.contains("/actuator/health/readiness"),
                "Deploy compose healthcheck should align to readiness endpoint");
    }

    @Test
    void deployWorkflowSupportsUatAndOperationalChecks() throws IOException {
        String workflow = Files.readString(ROOT.resolve(".github/workflows/deploy.yml"));

        assertTrue(workflow.contains("- uat"), "Deploy workflow should expose UAT environment");
        assertTrue(
                workflow.contains("ops/preflight/check-runtime-deps.sh"),
                "Deploy workflow should run runtime preflight checks");
        assertTrue(
                workflow.contains("ops/openapi/export-static.sh"),
                "Deploy workflow should export static OpenAPI artifact");
        assertTrue(
                workflow.contains("ops/release/release-drill.sh"),
                "Deploy workflow should support release drill execution");
        assertTrue(
                workflow.contains("judge-worker"),
                "Deploy workflow should pull logs and status for dedicated judge worker");
        assertTrue(
                workflow.contains("AUBB_JUDGE_QUEUE_DLQ_NAME"),
                "Deploy workflow should render DLQ related queue variables");
        assertTrue(
                workflow.contains("AUBB_REDIS_ENABLED") && workflow.contains("AUBB_REDIS_PASSWORD"),
                "Deploy workflow should render optional Redis envs");
        assertTrue(
                !workflow.contains("AUBB_REDIS_REALTIME_ENABLED"),
                "Deploy workflow should not render removed Redis realtime envs");
        assertTrue(
                workflow.contains("management.otlp.tracing.endpoint")
                        || workflow.contains("AUBB_OTLP_TRACING_ENDPOINT"),
                "Deploy workflow should render tracing endpoint variables");
    }
}
