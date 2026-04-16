package com.aubb.server.modules.assignment.application.bank;

import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionConfigView;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionOptionView;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import java.time.OffsetDateTime;
import java.util.List;

public record QuestionBankQuestionView(
        Long id,
        Long offeringId,
        String title,
        String prompt,
        AssignmentQuestionType questionType,
        Integer defaultScore,
        List<AssignmentQuestionOptionView> options,
        AssignmentQuestionConfigView config,
        List<String> tags,
        Boolean archived,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
