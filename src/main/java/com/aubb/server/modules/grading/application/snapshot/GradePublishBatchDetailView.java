package com.aubb.server.modules.grading.application.snapshot;

import java.util.List;

public record GradePublishBatchDetailView(
        GradePublishBatchSummaryView batch, List<GradePublishSnapshotView> snapshots) {}
