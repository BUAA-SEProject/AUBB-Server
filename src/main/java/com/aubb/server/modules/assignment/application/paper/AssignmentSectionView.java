package com.aubb.server.modules.assignment.application.paper;

import java.util.List;

public record AssignmentSectionView(
        Long id,
        Integer sectionOrder,
        String title,
        String description,
        Integer totalScore,
        List<AssignmentQuestionView> questions) {}
