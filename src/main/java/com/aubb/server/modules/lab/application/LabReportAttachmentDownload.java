package com.aubb.server.modules.lab.application;

public record LabReportAttachmentDownload(String originalFilename, String contentType, byte[] content) {}
