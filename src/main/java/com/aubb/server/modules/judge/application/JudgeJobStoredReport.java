package com.aubb.server.modules.judge.application;

import java.util.List;
import java.util.Map;

public record JudgeJobStoredReport(
        Map<String, Object> executionMetadata,
        List<JudgeJobCaseReportView> caseReports,
        String stdoutText,
        String stderrText) {}
