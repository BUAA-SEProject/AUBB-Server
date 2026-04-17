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
        assertTrue(Files.exists(ROOT.resolve(".github/workflows/ci.yml")), "Missing CI workflow");
        assertTrue(Files.exists(ROOT.resolve(".github/workflows/deploy.yml")), "Missing deploy workflow");
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
        assertTrue(!compose.contains("SPRING_DATA_REDIS_"), "Redis wiring should be removed from root compose");
        assertTrue(!compose.contains("\n  redis:\n"), "Redis service should be removed from root compose");
    }

    @Test
    void deploymentComposeRequiresVersionedAppImage() throws IOException {
        String deployCompose = Files.readString(ROOT.resolve("deploy/compose.yaml"));

        assertTrue(deployCompose.contains("AUBB_APP_IMAGE:?"), "Deploy compose must require explicit image version");
        assertTrue(deployCompose.contains("SPRING_DATASOURCE_URL:?"), "Deploy compose must require datasource wiring");
        assertTrue(deployCompose.contains("AUBB_JWT_SECRET:?"), "Deploy compose must require JWT secret");
        assertTrue(!deployCompose.contains("SPRING_DATA_REDIS_"), "Deploy compose should not require Redis envs");
    }
}
