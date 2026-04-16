package com.aubb.server.modules.grading.application;

import java.util.List;

public record BatchManualGradeResultView(Long assignmentId, int successCount, List<ManualGradeResultView> results) {}
