package com.aubb.server.modules.grading.application.gradebook;

import java.util.List;

public record GradebookReportView(
        GradebookPageView.ScopeView scope,
        OverviewView overview,
        List<AssignmentStatView> assignments,
        List<TeachingClassStatView> teachingClasses) {

    public record OverviewView(
            int assignmentCount,
            int studentCount,
            int applicableGradeCount,
            int submittedCount,
            int fullyGradedCount,
            int publishedCount,
            double submissionRate,
            double gradingRate,
            double publicationRate,
            double averageTotalFinalScore,
            double averageTotalScoreRate,
            double averageTotalWeightedScore,
            double averageWeightedScoreRate,
            List<ScoreBandView> scoreBands) {}

    public record AssignmentStatView(
            Long assignmentId,
            Long teachingClassId,
            String teachingClassName,
            String title,
            int maxScore,
            int gradeWeight,
            int applicableStudentCount,
            int submittedStudentCount,
            int fullyGradedStudentCount,
            int publishedStudentCount,
            double submissionRate,
            double gradingRate,
            double publicationRate,
            double averageSubmittedFinalScore,
            double averageSubmittedScoreRate,
            double averageSubmittedWeightedScore,
            List<ScoreBandView> scoreBands) {}

    public record TeachingClassStatView(
            Long teachingClassId,
            String teachingClassCode,
            String teachingClassName,
            int studentCount,
            int applicableAssignmentCount,
            int submittedAssignmentCount,
            int gradedAssignmentCount,
            int publishedAssignmentCount,
            double submissionRate,
            double gradingRate,
            double publicationRate,
            double averageTotalFinalScore,
            double averageTotalScoreRate,
            double averageTotalWeightedScore,
            double averageWeightedScoreRate,
            List<ScoreBandView> scoreBands) {}

    public record ScoreBandView(
            String bandCode,
            String label,
            int minPercentInclusive,
            Integer maxPercentExclusive,
            int studentCount,
            double studentRate) {}
}
