package com.aubb.server.modules.assignment.application.paper;

import java.util.List;

public record AssignmentPaperView(
        Integer sectionCount, Integer questionCount, Integer totalScore, List<AssignmentSectionView> sections) {}
