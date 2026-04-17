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
            String teachingClassName,
            Integer offeringRank,
            Integer teachingClassRank) {}

    public record SummaryView(
            int assignmentCount,
            int submittedCount,
            int gradedCount,
            int totalFinalScore,
            int totalMaxScore,
            double totalWeightedScore,
            int totalWeight,
            double weightedScoreRate) {}

    public record AssignmentGradeView(
            GradebookPageView.AssignmentColumnView assignment, GradebookPageView.GradeCellView grade) {}
}
