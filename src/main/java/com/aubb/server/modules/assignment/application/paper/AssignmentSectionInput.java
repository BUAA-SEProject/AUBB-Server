package com.aubb.server.modules.assignment.application.paper;

import java.util.List;

public record AssignmentSectionInput(String title, String description, List<AssignmentQuestionInput> questions) {}
