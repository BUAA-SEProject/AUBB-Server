package com.aubb.server.modules.lab.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LabReportLifecyclePolicyTests {

    private final LabReportLifecyclePolicy policy = new LabReportLifecyclePolicy();

    @Test
    void studentCanOnlyMutateDraftOrSubmittedReport() {
        assertThat(policy.canStudentMutate(null)).isTrue();
        assertThat(policy.canStudentMutate(LabReportStatus.DRAFT)).isTrue();
        assertThat(policy.canStudentMutate(LabReportStatus.SUBMITTED)).isTrue();
        assertThat(policy.canStudentMutate(LabReportStatus.REVIEWED)).isFalse();
        assertThat(policy.canStudentMutate(LabReportStatus.PUBLISHED)).isFalse();
    }

    @Test
    void teacherReviewAndPublishRequireExpectedStatus() {
        assertThat(policy.canTeacherReview(LabReportStatus.SUBMITTED)).isTrue();
        assertThat(policy.canTeacherReview(LabReportStatus.REVIEWED)).isTrue();
        assertThat(policy.canTeacherReview(LabReportStatus.DRAFT)).isFalse();

        assertThat(policy.canTeacherPublish(LabReportStatus.REVIEWED)).isTrue();
        assertThat(policy.canTeacherPublish(LabReportStatus.PUBLISHED)).isTrue();
        assertThat(policy.canTeacherPublish(LabReportStatus.SUBMITTED)).isFalse();
    }

    @Test
    void studentSaveResolvesDraftOrSubmittedStatus() {
        assertThat(policy.nextStudentStatus(false)).isEqualTo(LabReportStatus.DRAFT);
        assertThat(policy.nextStudentStatus(true)).isEqualTo(LabReportStatus.SUBMITTED);
    }
}
