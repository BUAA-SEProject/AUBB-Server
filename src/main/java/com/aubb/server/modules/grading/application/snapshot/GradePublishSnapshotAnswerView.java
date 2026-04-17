package com.aubb.server.modules.grading.application.snapshot;

import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import java.time.OffsetDateTime;

public record GradePublishSnapshotAnswerView(
        Long answerId,
        Long assignmentQuestionId,
        String questionTitle,
        AssignmentQuestionType questionType,
        Integer autoScore,
        Integer manualScore,
        Integer finalScore,
        SubmissionAnswerGradingStatus gradingStatus,
        String feedbackText,
        Long gradedByUserId,
        OffsetDateTime gradedAt) {}
