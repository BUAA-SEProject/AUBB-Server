package com.aubb.server.modules.grading.application.gradebook;

import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

public record GradebookPageView(
        ScopeView scope,
        SummaryView summary,
        List<AssignmentColumnView> assignmentColumns,
        List<StudentRowView> items,
        long total,
        long page,
        long pageSize) {

    public record ScopeView(
            Long offeringId, Long teachingClassId, String teachingClassCode, String teachingClassName) {}

    public record SummaryView(
            int assignmentCount, int studentCount, int submittedCount, int fullyGradedCount, int publishedCount) {}

    public record AssignmentColumnView(
            Long assignmentId,
            Long teachingClassId,
            String teachingClassName,
            String title,
            String status,
            OffsetDateTime openAt,
            OffsetDateTime dueAt,
            Integer maxScore,
            Integer gradeWeight,
            Boolean gradePublished) {}

    public record StudentRowView(
            Long userId,
            String username,
            String displayName,
            Long teachingClassId,
            String teachingClassCode,
            String teachingClassName,
            Integer totalFinalScore,
            Integer totalMaxScore,
            double totalWeightedScore,
            Integer totalWeight,
            double weightedScoreRate,
            Integer offeringRank,
            Integer teachingClassRank,
            Integer submittedAssignmentCount,
            Integer gradedAssignmentCount,
            List<GradeCellView> grades) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GradeCellView(
            Long assignmentId,
            Boolean applicable,
            Boolean submitted,
            Long latestSubmissionId,
            Integer latestAttemptNo,
            OffsetDateTime submittedAt,
            SubmissionScoreSummaryView scoreSummary,
            Integer finalScore,
            Integer maxScore,
            Double weightedScore,
            Boolean fullyGraded,
            Boolean gradePublished) {}
}
