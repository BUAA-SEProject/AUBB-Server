package com.aubb.server.modules.grading.application;

public record BatchGradeImportErrorView(Long rowNumber, Long submissionId, Long answerId, String message) {}
