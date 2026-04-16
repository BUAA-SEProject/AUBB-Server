package com.aubb.server.modules.submission.application.answer;

import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionAnswerView(
        Long id,
        Long assignmentQuestionId,
        String questionTitle,
        AssignmentQuestionType questionType,
        String answerText,
        List<String> selectedOptionKeys,
        List<Long> artifactIds,
        ProgrammingLanguage programmingLanguage,
        Integer autoScore,
        Integer manualScore,
        Integer finalScore,
        SubmissionAnswerGradingStatus gradingStatus,
        String feedbackText,
        Long gradedByUserId,
        OffsetDateTime gradedAt) {}
