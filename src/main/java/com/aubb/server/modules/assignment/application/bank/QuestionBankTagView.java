package com.aubb.server.modules.assignment.application.bank;

import java.time.OffsetDateTime;

public record QuestionBankTagView(
        Long id,
        Long offeringId,
        String name,
        long activeQuestionCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
