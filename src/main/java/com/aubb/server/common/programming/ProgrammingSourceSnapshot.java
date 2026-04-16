package com.aubb.server.common.programming;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.util.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProgrammingSourceSnapshot(String entryFilePath, List<ProgrammingSourceFile> files) {

    public ProgrammingSourceSnapshot {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static ProgrammingSourceSnapshot fromInput(
            ProgrammingLanguage programmingLanguage,
            String codeText,
            String entryFilePath,
            List<ProgrammingSourceFile> files) {
        List<ProgrammingSourceFile> normalizedFiles = normalizeFiles(files);
        String normalizedEntryFilePath = resolveEntryFilePath(programmingLanguage, entryFilePath, normalizedFiles);
        if (normalizedFiles.isEmpty() && StringUtils.hasText(codeText)) {
            return new ProgrammingSourceSnapshot(
                    normalizedEntryFilePath,
                    List.of(new ProgrammingSourceFile(normalizedEntryFilePath, codeText.stripTrailing())));
        }
        return new ProgrammingSourceSnapshot(normalizedEntryFilePath, normalizedFiles);
    }

    public String entryCodeText() {
        return files.stream()
                .filter(file -> entryFilePath.equals(file.path()))
                .map(ProgrammingSourceFile::content)
                .findFirst()
                .orElse(null);
    }

    public static String defaultEntryFilePath(ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage == null) {
            return "main.py";
        }
        return switch (programmingLanguage) {
            case PYTHON3 -> "main.py";
            case JAVA17 -> "Main.java";
            case CPP17 -> "main.cpp";
        };
    }

    private static String resolveEntryFilePath(
            ProgrammingLanguage programmingLanguage, String entryFilePath, List<ProgrammingSourceFile> files) {
        if (StringUtils.hasText(entryFilePath)) {
            return entryFilePath.trim();
        }
        String defaultEntryFilePath = defaultEntryFilePath(programmingLanguage);
        if (files == null || files.isEmpty()) {
            return defaultEntryFilePath;
        }
        boolean containsDefaultEntry = files.stream().anyMatch(file -> defaultEntryFilePath.equals(file.path()));
        return containsDefaultEntry ? defaultEntryFilePath : files.getFirst().path();
    }

    private static List<ProgrammingSourceFile> normalizeFiles(List<ProgrammingSourceFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .filter(java.util.Objects::nonNull)
                .map(file -> new ProgrammingSourceFile(
                        file.path() == null ? null : file.path().trim(), file.content() == null ? "" : file.content()))
                .toList();
    }
}
