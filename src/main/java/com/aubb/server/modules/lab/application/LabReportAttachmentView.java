package com.aubb.server.modules.lab.application;

import java.time.OffsetDateTime;

public record LabReportAttachmentView(
        Long id,
        Long labId,
        Long labReportId,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        OffsetDateTime uploadedAt) {}
