package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import java.util.List;

public record AssignmentQuestionView(
        Long id,
        Long sourceQuestionId,
        Integer questionOrder,
        String title,
        String prompt,
        AssignmentQuestionType questionType,
        Integer score,
        List<AssignmentQuestionOptionView> options,
        AssignmentQuestionConfigView config) {}
