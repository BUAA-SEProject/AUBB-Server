package com.aubb.server.modules.lab.domain;

public class LabReportLifecyclePolicy {

    public boolean canStudentMutate(LabReportStatus currentStatus) {
        return currentStatus == null
                || currentStatus == LabReportStatus.DRAFT
                || currentStatus == LabReportStatus.SUBMITTED;
    }

    public LabReportStatus nextStudentStatus(boolean submit) {
        return submit ? LabReportStatus.SUBMITTED : LabReportStatus.DRAFT;
    }

    public boolean canTeacherReview(LabReportStatus currentStatus) {
        return currentStatus == LabReportStatus.SUBMITTED || currentStatus == LabReportStatus.REVIEWED;
    }

    public boolean canTeacherPublish(LabReportStatus currentStatus) {
        return currentStatus == LabReportStatus.REVIEWED || currentStatus == LabReportStatus.PUBLISHED;
    }
}
