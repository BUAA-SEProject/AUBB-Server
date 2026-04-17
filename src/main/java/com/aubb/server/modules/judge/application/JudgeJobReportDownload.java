package com.aubb.server.modules.judge.application;

public record JudgeJobReportDownload(String filename, String contentType, byte[] content) {}
