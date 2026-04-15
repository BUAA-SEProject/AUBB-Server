package com.aubb.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RepositoryStructureTests {

    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path INTEGRATION_TEST_ROOT = ROOT.resolve("src/test/java/com/aubb/server/integration");

    private static final List<String> REQUIRED_MODULE_ROOTS = List.of(
            "src/main/java/com/aubb/server/modules/assignment",
            "src/main/java/com/aubb/server/modules/identityaccess",
            "src/main/java/com/aubb/server/modules/organization",
            "src/main/java/com/aubb/server/modules/course",
            "src/main/java/com/aubb/server/modules/submission",
            "src/main/java/com/aubb/server/modules/platformconfig",
            "src/main/java/com/aubb/server/modules/audit");

    @Test
    void businessCodeUsesModuleFirstLayout() throws IOException {
        List<String> missingModuleRoots = new ArrayList<>();
        for (String relativePath : REQUIRED_MODULE_ROOTS) {
            if (Files.notExists(ROOT.resolve(relativePath))) {
                missingModuleRoots.add(relativePath);
            }
        }

        List<String> unexpectedLegacyPackages = new ArrayList<>();
        for (String relativePath : List.of(
                "src/main/java/com/aubb/server/api",
                "src/main/java/com/aubb/server/application",
                "src/main/java/com/aubb/server/domain")) {
            if (Files.exists(ROOT.resolve(relativePath))) {
                unexpectedLegacyPackages.add(relativePath);
            }
        }

        Path infrastructureRoot = ROOT.resolve("src/main/java/com/aubb/server/infrastructure");
        if (Files.exists(infrastructureRoot)) {
            try (Stream<Path> paths = Files.walk(infrastructureRoot)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> ROOT.relativize(path).toString().replace('\\', '/'))
                        .filter(path -> !path.startsWith("src/main/java/com/aubb/server/infrastructure/persistence/"))
                        .forEach(unexpectedLegacyPackages::add);
            }
        }

        assertTrue(
                missingModuleRoots.isEmpty(), () -> "Missing module roots: " + String.join(", ", missingModuleRoots));
        assertTrue(
                unexpectedLegacyPackages.isEmpty(),
                () -> "Legacy layer-first business packages still present: "
                        + String.join(", ", unexpectedLegacyPackages));
    }

    @Test
    void integrationTestsUseDedicatedPackage() {
        assertTrue(
                Files.exists(INTEGRATION_TEST_ROOT),
                () -> "Missing integration test root: " + ROOT.relativize(INTEGRATION_TEST_ROOT));
        assertTrue(
                Files.notExists(ROOT.resolve("src/test/java/com/aubb/server/api")),
                "Legacy test package src/test/java/com/aubb/server/api should not exist");
    }

    @Test
    void denseModuleLayersUseResponsibilitySubpackages() throws IOException {
        Map<String, Integer> directFileLimits = new LinkedHashMap<>();
        directFileLimits.put("src/main/java/com/aubb/server/modules/course/application", 3);
        directFileLimits.put("src/main/java/com/aubb/server/modules/course/domain", 0);
        directFileLimits.put("src/main/java/com/aubb/server/modules/course/infrastructure", 0);
        directFileLimits.put("src/main/java/com/aubb/server/modules/identityaccess/application/user", 3);
        directFileLimits.put("src/main/java/com/aubb/server/modules/identityaccess/domain", 0);
        directFileLimits.put("src/main/java/com/aubb/server/modules/identityaccess/infrastructure", 0);

        List<String> limitViolations = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : directFileLimits.entrySet()) {
            int directFileCount = countDirectFiles(ROOT.resolve(entry.getKey()));
            if (directFileCount > entry.getValue()) {
                limitViolations.add(entry.getKey() + " has " + directFileCount + " files");
            }
        }

        List<String> missingResponsibilitySubpackages = new ArrayList<>();
        for (String relativePath : List.of(
                "src/main/java/com/aubb/server/modules/course/application/view",
                "src/main/java/com/aubb/server/modules/course/application/command",
                "src/main/java/com/aubb/server/modules/course/application/result",
                "src/main/java/com/aubb/server/modules/course/domain/term",
                "src/main/java/com/aubb/server/modules/course/domain/catalog",
                "src/main/java/com/aubb/server/modules/course/domain/offering",
                "src/main/java/com/aubb/server/modules/course/domain/member",
                "src/main/java/com/aubb/server/modules/course/domain/teaching",
                "src/main/java/com/aubb/server/modules/course/infrastructure/term",
                "src/main/java/com/aubb/server/modules/course/infrastructure/catalog",
                "src/main/java/com/aubb/server/modules/course/infrastructure/offering",
                "src/main/java/com/aubb/server/modules/course/infrastructure/member",
                "src/main/java/com/aubb/server/modules/course/infrastructure/teaching",
                "src/main/java/com/aubb/server/modules/identityaccess/application/user/view",
                "src/main/java/com/aubb/server/modules/identityaccess/application/user/command",
                "src/main/java/com/aubb/server/modules/identityaccess/application/user/result",
                "src/main/java/com/aubb/server/modules/identityaccess/domain/account",
                "src/main/java/com/aubb/server/modules/identityaccess/domain/profile",
                "src/main/java/com/aubb/server/modules/identityaccess/domain/governance",
                "src/main/java/com/aubb/server/modules/identityaccess/domain/membership",
                "src/main/java/com/aubb/server/modules/identityaccess/infrastructure/user",
                "src/main/java/com/aubb/server/modules/identityaccess/infrastructure/profile",
                "src/main/java/com/aubb/server/modules/identityaccess/infrastructure/membership",
                "src/main/java/com/aubb/server/modules/identityaccess/infrastructure/role")) {
            if (Files.notExists(ROOT.resolve(relativePath))) {
                missingResponsibilitySubpackages.add(relativePath);
            }
        }

        assertTrue(
                limitViolations.isEmpty(),
                () -> "Dense module layers should use responsibility subpackages: "
                        + String.join(", ", limitViolations));
        assertTrue(
                missingResponsibilitySubpackages.isEmpty(),
                () -> "Missing responsibility subpackages: " + String.join(", ", missingResponsibilitySubpackages));
    }

    private int countDirectFiles(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return 0;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return (int) paths.filter(Files::isRegularFile).count();
        }
    }
}
