package com.aubb.server.modules.assignment.application.paper;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentQuestionOptionView(String optionKey, String content, Boolean correct) {}
