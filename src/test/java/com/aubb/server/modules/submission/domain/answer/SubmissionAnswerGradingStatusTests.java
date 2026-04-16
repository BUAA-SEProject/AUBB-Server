package com.aubb.server.modules.submission.domain.answer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubmissionAnswerGradingStatusTests {

    @Test
    void onlyPendingStatusesAreReportedAsPending() {
        assertThat(SubmissionAnswerGradingStatus.PENDING_MANUAL.isPending()).isTrue();
        assertThat(SubmissionAnswerGradingStatus.PENDING_PROGRAMMING_JUDGE.isPending())
                .isTrue();
        assertThat(SubmissionAnswerGradingStatus.PROGRAMMING_JUDGED.isPending()).isFalse();
        assertThat(SubmissionAnswerGradingStatus.PROGRAMMING_JUDGE_FAILED.isPending())
                .isFalse();
    }
}
