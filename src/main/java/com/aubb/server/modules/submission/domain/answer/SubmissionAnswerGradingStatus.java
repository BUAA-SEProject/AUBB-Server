package com.aubb.server.modules.submission.domain.answer;

public enum SubmissionAnswerGradingStatus {
    AUTO_GRADED,
    MANUALLY_GRADED,
    PROGRAMMING_JUDGED,
    PROGRAMMING_JUDGE_FAILED,
    PENDING_MANUAL,
    PENDING_PROGRAMMING_JUDGE;

    public boolean isPending() {
        return this == PENDING_MANUAL || this == PENDING_PROGRAMMING_JUDGE;
    }
}
