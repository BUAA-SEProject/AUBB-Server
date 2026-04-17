package com.aubb.server.modules.assignment.application.paper;

import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import java.util.List;
import java.util.Set;

public record AssignmentQuestionSnapshot(
        Long id,
        Long assignmentId,
        Long sourceQuestionId,
        Integer questionOrder,
        String title,
        String prompt,
        AssignmentQuestionType questionType,
        Integer score,
        List<AssignmentQuestionOptionView> options,
        Set<String> correctOptionKeys,
        AssignmentQuestionConfigInput config) {}
