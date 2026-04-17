package com.aubb.server.modules.grading.application.appeal;

import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.grading.domain.appeal.GradeAppealStatus;
import java.time.OffsetDateTime;

public record GradeAppealView(
        Long id,
        Long offeringId,
        Long teachingClassId,
        Long assignmentId,
        String assignmentTitle,
        Long submissionId,
        Long submissionAnswerId,
        Long assignmentQuestionId,
        String questionTitle,
        AssignmentQuestionType questionType,
        Long studentUserId,
        String studentUsername,
        String studentDisplayName,
        GradeAppealStatus status,
        String appealReason,
        String responseText,
        Integer currentFinalScore,
        Integer resolvedScore,
        String answerFeedbackText,
        Long respondedByUserId,
        OffsetDateTime respondedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
