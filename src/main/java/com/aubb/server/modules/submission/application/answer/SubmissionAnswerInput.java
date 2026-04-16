package com.aubb.server.modules.submission.application.answer;

import com.aubb.server.modules.assignment.domain.question.ProgrammingLanguage;
import java.util.List;

public record SubmissionAnswerInput(
        Long assignmentQuestionId,
        String answerText,
        List<String> selectedOptionKeys,
        List<Long> artifactIds,
        ProgrammingLanguage programmingLanguage) {}
