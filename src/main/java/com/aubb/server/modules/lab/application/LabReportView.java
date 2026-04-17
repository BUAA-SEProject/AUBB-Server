package com.aubb.server.modules.lab.application;

import com.aubb.server.modules.lab.domain.LabReportStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record LabReportView(
        Long id,
        Long labId,
        LabReportStatus status,
        String reportContentText,
        String teacherAnnotationText,
        String teacherCommentText,
        LabUserSummaryView student,
        LabUserSummaryView reviewer,
        List<LabReportAttachmentView> attachments,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
