package com.aubb.server.modules.grading.application.gradebook;

public record GradebookExportContent(String filename, String contentType, byte[] content) {}
