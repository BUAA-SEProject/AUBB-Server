package com.aubb.server.modules.lab.application;

import com.aubb.server.modules.lab.domain.LabReportStatus;
import java.time.OffsetDateTime;

public record LabReportSummaryView(
        Long id,
        Long labId,
        LabUserSummaryView student,
        LabReportStatus status,
        int attachmentCount,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt) {}
