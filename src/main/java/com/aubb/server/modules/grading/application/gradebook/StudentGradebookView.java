package com.aubb.server.modules.grading.application.gradebook;

import java.util.List;

public record StudentGradebookView(
        GradebookPageView.ScopeView scope,
        StudentView student,
        SummaryView summary,
        List<AssignmentGradeView> assignments) {

    public record StudentView(
            Long userId,
            String username,
            String displayName,
            Long teachingClassId,
            String teachingClassCode,
            String teachingClassName) {}

    public record SummaryView(
            int assignmentCount, int submittedCount, int gradedCount, int totalFinalScore, int totalMaxScore) {}

    public record AssignmentGradeView(
            GradebookPageView.AssignmentColumnView assignment, GradebookPageView.GradeCellView grade) {}
}
