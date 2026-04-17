package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import java.util.List;

public record AssignmentQuestionInput(
        Long bankQuestionId,
        String title,
        String prompt,
        AssignmentQuestionType questionType,
        Integer score,
        List<AssignmentQuestionOptionInput> options,
        AssignmentQuestionConfigInput config) {}
