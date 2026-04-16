package com.aubb.server.modules.grading.application;

import java.util.List;

public record BatchGradeImportResultView(
        Long assignmentId,
        int totalCount,
        int successCount,
        int failureCount,
        List<BatchGradeImportErrorView> errors) {}
