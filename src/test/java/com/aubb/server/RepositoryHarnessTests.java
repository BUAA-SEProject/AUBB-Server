package com.aubb.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RepositoryHarnessTests {

    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();

    private static final List<String> REQUIRED_PATHS = List.of(
            "README.md",
            "AGENTS.md",
            "ARCHITECTURE.md",
            "docs/index.md",
            "docs/development-workflow.md",
            "docs/plan.md",
            "docs/design.md",
            "docs/plans.md",
            "docs/product-sense.md",
            "docs/project-skills.md",
            "docs/quality-score.md",
            "docs/reliability.md",
            "docs/security.md",
            "docs/design-docs/index.md",
            "docs/design-docs/core-beliefs.md",
            "docs/design-docs/adr-0003-user-system-boundaries.md",
            "docs/design-docs/adr-0004-module-first-modular-monolith.md",
            "docs/design-docs/adr-0005-course-module-first-slice.md",
            "docs/exec-plans/active/README.md",
            "docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md",
            "docs/exec-plans/tech-debt-tracker.md",
            "docs/generated/db-schema.md",
            "docs/product-specs/index.md",
            "docs/product-specs/platform-baseline.md",
            "docs/product-specs/course-system.md",
            "docs/references/openai-harness-engineering-notes.md",
            ".github/workflows/harness.yml");

    private static final List<String> ROOT_MARKDOWN_FILES = List.of("README.md", "AGENTS.md", "ARCHITECTURE.md");

    private static final List<String> FORBIDDEN_PROCESS_DOCS =
            List.of("docs/task_plan.md", "docs/findings.md", "docs/progress.md");

    private static final List<String> REQUIRED_MODULE_ROOTS = List.of(
            "src/main/java/com/aubb/server/modules/identityaccess",
            "src/main/java/com/aubb/server/modules/organization",
            "src/main/java/com/aubb/server/modules/course",
            "src/main/java/com/aubb/server/modules/platformconfig",
            "src/main/java/com/aubb/server/modules/audit");

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[[^\\]]+\\]\\(([^)#]+)(?:#[^)]+)?\\)");

    @Test
    void requiredHarnessFilesExist() {
        List<String> missing = new ArrayList<>();
        for (String relativePath : REQUIRED_PATHS) {
            if (Files.notExists(ROOT.resolve(relativePath))) {
                missing.add(relativePath);
            }
        }

        assertTrue(missing.isEmpty(), () -> "Missing harness paths: " + String.join(", ", missing));
    }

    @Test
    void architectureDocCapturesCurrentRuntime() throws IOException {
        String architecture = Files.readString(ROOT.resolve("ARCHITECTURE.md"));

        assertTrue(architecture.contains("Spring Boot 4"));
        assertTrue(architecture.contains("Java 25"));
        assertTrue(architecture.contains("PostgreSQL"));
        assertTrue(architecture.contains("RabbitMQ"));
        assertTrue(architecture.contains("Redis"));
    }

    @Test
    void globalDocsDoNotContainProcessMemoryFiles() {
        List<String> unexpected = new ArrayList<>();
        for (String relativePath : FORBIDDEN_PROCESS_DOCS) {
            if (Files.exists(ROOT.resolve(relativePath))) {
                unexpected.add(relativePath);
            }
        }

        assertTrue(unexpected.isEmpty(), () -> "Process docs must not live in docs/: " + String.join(", ", unexpected));
    }

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
    void markdownLinksResolveInsideHarnessDocs() throws IOException {
        List<Path> markdownFiles = new ArrayList<>();
        for (String relativePath : ROOT_MARKDOWN_FILES) {
            markdownFiles.add(ROOT.resolve(relativePath));
        }

        try (Stream<Path> docs = Files.walk(ROOT.resolve("docs"))) {
            docs.filter(path -> path.toString().endsWith(".md")).forEach(markdownFiles::add);
        }

        List<String> brokenLinks = new ArrayList<>();
        for (Path markdownFile : markdownFiles) {
            String content = Files.readString(markdownFile);
            Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(content);
            while (matcher.find()) {
                String target = matcher.group(1).trim();
                if (target.isBlank()
                        || target.startsWith("http://")
                        || target.startsWith("https://")
                        || target.startsWith("mailto:")
                        || target.startsWith("#")) {
                    continue;
                }

                Path resolved = markdownFile.getParent().resolve(target).normalize();
                if (Files.notExists(resolved)) {
                    brokenLinks.add(ROOT.relativize(markdownFile) + " -> " + target);
                }
            }
        }

        assertTrue(brokenLinks.isEmpty(), () -> "Broken markdown links: " + String.join(", ", brokenLinks));
    }
}
