package com.aubb.server.modules.submission.application.answer;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionScoreSummaryView(
        Integer autoScoredScore,
        Integer manualScoredScore,
        Integer finalScore,
        Integer maxScore,
        Integer pendingManualCount,
        Integer pendingProgrammingCount,
        Boolean fullyGraded,
        Boolean gradePublished) {}
