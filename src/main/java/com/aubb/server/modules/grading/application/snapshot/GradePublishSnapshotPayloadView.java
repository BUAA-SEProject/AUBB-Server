package com.aubb.server.modules.grading.application.snapshot;

import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import java.time.OffsetDateTime;
import java.util.List;

public record GradePublishSnapshotPayloadView(
        Long assignmentId,
        Long offeringId,
        Long teachingClassId,
        StudentView student,
        SubmissionView submission,
        SubmissionScoreSummaryView scoreSummary,
        List<GradePublishSnapshotAnswerView> answers) {

    public record StudentView(
            Long userId,
            String username,
            String displayName,
            Long teachingClassId,
            String teachingClassCode,
            String teachingClassName) {}

    public record SubmissionView(
            Long submissionId, String submissionNo, Integer attemptNo, OffsetDateTime submittedAt) {}
}
