package com.aubb.server.modules.grading.application;

import com.aubb.server.modules.submission.application.answer.SubmissionAnswerView;
import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;

public record ManualGradeResultView(SubmissionAnswerView answer, SubmissionScoreSummaryView scoreSummary) {}
