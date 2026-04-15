package com.aubb.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RepositoryStructureTests {

    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

    private static final List<String> REQUIRED_MODULE_ROOTS = List.of(
            "src/main/java/com/aubb/server/modules/identityaccess",
            "src/main/java/com/aubb/server/modules/organization",
            "src/main/java/com/aubb/server/modules/course",
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
}
