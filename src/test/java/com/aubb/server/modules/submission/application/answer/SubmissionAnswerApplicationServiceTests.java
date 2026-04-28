package com.aubb.server.modules.submission.application.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aubb.server.common.programming.ProgrammingSourceFile;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerEntity;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SubmissionAnswerApplicationServiceTests {

    @Mock
    private SubmissionAnswerMapper submissionAnswerMapper;

    @Mock
    private AssignmentPaperApplicationService assignmentPaperApplicationService;

    private SubmissionAnswerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SubmissionAnswerApplicationService(
                submissionAnswerMapper, assignmentPaperApplicationService, new ObjectMapper());
    }

    @Test
    void loadAnswerViewsShouldMaskSensitiveProgrammingContentWithoutSensitiveGrant() {
        when(submissionAnswerMapper.selectList(any())).thenReturn(List.of(programmingAnswer()));
        when(assignmentPaperApplicationService.loadQuestionSnapshots(300L)).thenReturn(List.of(programmingQuestion()));

        SubmissionAnswerView view =
                service.loadAnswerViews(22L, 300L, true, false).getFirst();

        assertThat(view.questionType()).isEqualTo(AssignmentQuestionType.PROGRAMMING);
        assertThat(view.answerText()).isNull();
        assertThat(view.artifactIds()).isNull();
        assertThat(view.entryFilePath()).isNull();
        assertThat(view.files()).isNull();
        assertThat(view.manualScore()).isEqualTo(90);
        assertThat(view.finalScore()).isEqualTo(90);
        assertThat(view.feedbackText()).isEqualTo("源码正确，复杂度达标");
    }

    @Test
    void loadAnswerViewsShouldHideManualGradeFieldsWhenNonObjectiveScoresAreSuppressed() {
        when(submissionAnswerMapper.selectList(any())).thenReturn(List.of(programmingAnswer()));
        when(assignmentPaperApplicationService.loadQuestionSnapshots(300L)).thenReturn(List.of(programmingQuestion()));

        SubmissionAnswerView view =
                service.loadAnswerViews(22L, 300L, false, true).getFirst();

        assertThat(view.answerText()).isEqualTo("print('hello')");
        assertThat(view.artifactIds()).containsExactly(501L);
        assertThat(view.entryFilePath()).isEqualTo("main.py");
        assertThat(view.files()).containsExactly(new ProgrammingSourceFile("main.py", "print('hello')"));
        assertThat(view.manualScore()).isNull();
        assertThat(view.finalScore()).isNull();
        assertThat(view.feedbackText()).isNull();
        assertThat(view.gradedByUserId()).isNull();
        assertThat(view.gradedAt()).isNull();
    }

    private AssignmentQuestionSnapshot programmingQuestion() {
        return new AssignmentQuestionSnapshot(
                101L,
                300L,
                null,
                1,
                "编程题",
                "实现 hello world",
                AssignmentQuestionType.PROGRAMMING,
                100,
                List.of(),
                java.util.Set.of(),
                null);
    }

    private SubmissionAnswerEntity programmingAnswer() {
        SubmissionAnswerEntity entity = new SubmissionAnswerEntity();
        entity.setId(11L);
        entity.setSubmissionId(22L);
        entity.setAssignmentQuestionId(101L);
        entity.setAnswerText("print('hello')");
        entity.setAnswerPayloadJson("""
                {
                  "selectedOptionKeys":[],
                  "artifactIds":[501],
                  "programmingLanguage":"PYTHON3",
                  "entryFilePath":"main.py",
                  "files":[{"path":"main.py","content":"print('hello')"}]
                }
                """);
        entity.setManualScore(90);
        entity.setFinalScore(90);
        entity.setGradingStatus(SubmissionAnswerGradingStatus.MANUALLY_GRADED.name());
        entity.setFeedbackText("源码正确，复杂度达标");
        entity.setGradedByUserId(7L);
        entity.setGradedAt(OffsetDateTime.parse("2026-04-18T20:00:00+08:00"));
        return entity;
    }
}
