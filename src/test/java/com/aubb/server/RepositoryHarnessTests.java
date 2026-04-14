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
            "docs/exec-plans/active/README.md",
            "docs/exec-plans/completed/2026-04-14-harness-engineering-bootstrap.md",
            "docs/exec-plans/tech-debt-tracker.md",
            "docs/generated/db-schema.md",
            "docs/product-specs/index.md",
            "docs/product-specs/platform-baseline.md",
            "docs/references/openai-harness-engineering-notes.md",
            ".github/workflows/harness.yml");

    private static final List<String> ROOT_MARKDOWN_FILES = List.of(
            "README.md",
            "AGENTS.md",
            "ARCHITECTURE.md");

    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("\\[[^\\]]+\\]\\(([^)#]+)(?:#[^)]+)?\\)");

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
