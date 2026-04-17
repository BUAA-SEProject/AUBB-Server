package com.aubb.server.modules.assignment.application.judge;

public record AssignmentJudgeCaseInput(String stdinText, String expectedStdout, Integer score) {}
