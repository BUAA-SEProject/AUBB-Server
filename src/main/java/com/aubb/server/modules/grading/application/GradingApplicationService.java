package com.aubb.server.modules.grading.application;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerView;
import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerEntity;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GradingApplicationService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final SubmissionAnswerApplicationService submissionAnswerApplicationService;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional
    public ManualGradeResultView gradeAnswer(
            Long submissionId,
            Long answerId,
            Integer score,
            String feedbackText,
            AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), submission.getTeachingClassId());

        SubmissionAnswerEntity answer = requireAnswer(answerId);
        if (!Objects.equals(answer.getSubmissionId(), submissionId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_ANSWER_SCOPE_INVALID", "答案不属于当前提交");
        }

        AssignmentQuestionSnapshot question =
                assignmentPaperApplicationService.loadQuestionSnapshots(assignment.getId()).stream()
                        .filter(candidate -> Objects.equals(candidate.id(), answer.getAssignmentQuestionId()))
                        .findFirst()
                        .orElseThrow(() ->
                                new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_QUESTION_NOT_FOUND", "题目不存在"));
        validateManualGrade(question, score);

        OffsetDateTime gradedAt = OffsetDateTime.now();
        answer.setManualScore(score);
        answer.setFinalScore(score);
        answer.setGradingStatus(SubmissionAnswerGradingStatus.MANUALLY_GRADED.name());
        answer.setFeedbackText(StringUtils.hasText(feedbackText) ? feedbackText.trim() : null);
        answer.setGradedByUserId(principal.getUserId());
        answer.setGradedAt(gradedAt);
        submissionAnswerMapper.updateById(answer);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignment.getId());
        metadata.put("submissionId", submissionId);
        metadata.put("assignmentQuestionId", answer.getAssignmentQuestionId());
        metadata.put("score", score);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.SUBMISSION_ANSWER_GRADED,
                "SUBMISSION_ANSWER",
                String.valueOf(answer.getId()),
                AuditResult.SUCCESS,
                metadata);

        SubmissionAnswerView answerView =
                submissionAnswerApplicationService.loadAnswerViews(submissionId, assignment.getId()).stream()
                        .filter(candidate -> Objects.equals(candidate.id(), answerId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "SUBMISSION_ANSWER_VIEW_MISSING", "批改结果无法读取"));
        SubmissionScoreSummaryView scoreSummary = submissionAnswerApplicationService.loadScoreSummary(
                submissionId, assignment.getId(), true, assignment.getGradePublishedAt() != null);
        return new ManualGradeResultView(answerView, scoreSummary);
    }

    @Transactional
    public AssignmentGradePublicationView publishAssignmentGrades(
            Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        assertGradesReady(assignmentId);
        if (assignment.getGradePublishedAt() == null) {
            assignment.setGradePublishedAt(OffsetDateTime.now());
            assignment.setGradePublishedByUserId(principal.getUserId());
            assignmentMapper.updateById(assignment);
            auditLogApplicationService.record(
                    principal.getUserId(),
                    AuditAction.ASSIGNMENT_GRADES_PUBLISHED,
                    "ASSIGNMENT",
                    String.valueOf(assignmentId),
                    AuditResult.SUCCESS,
                    Map.of("offeringId", assignment.getOfferingId()));
        }
        return new AssignmentGradePublicationView(
                assignment.getId(), assignment.getGradePublishedByUserId(), assignment.getGradePublishedAt());
    }

    private void assertGradesReady(Long assignmentId) {
        List<Long> submissionIds = submissionMapper
                .selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                        .eq(SubmissionEntity::getAssignmentId, assignmentId)
                        .select(SubmissionEntity::getId))
                .stream()
                .map(SubmissionEntity::getId)
                .toList();
        if (submissionIds.isEmpty()) {
            return;
        }
        Long pendingCount = submissionAnswerMapper.selectCount(Wrappers.<SubmissionAnswerEntity>lambdaQuery()
                .in(SubmissionAnswerEntity::getSubmissionId, submissionIds)
                .in(
                        SubmissionAnswerEntity::getGradingStatus,
                        List.of(
                                SubmissionAnswerGradingStatus.PENDING_MANUAL.name(),
                                SubmissionAnswerGradingStatus.PENDING_PROGRAMMING_JUDGE.name())));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_GRADES_NOT_READY", "当前作业仍有未完成批改或评测的答案");
        }
    }

    private void validateManualGrade(AssignmentQuestionSnapshot question, Integer score) {
        if (score == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_GRADE_SCORE_REQUIRED", "人工批改必须提供分数");
        }
        if (score < 0 || score > question.score()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_GRADE_SCORE_INVALID", "人工批改分数超出题目分值范围");
        }
        if (AssignmentQuestionType.SINGLE_CHOICE.equals(question.questionType())
                || AssignmentQuestionType.MULTIPLE_CHOICE.equals(question.questionType())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "SUBMISSION_GRADE_OBJECTIVE_NOT_SUPPORTED", "客观题不支持人工批改");
        }
    }

    private SubmissionEntity requireSubmission(Long submissionId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在");
        }
        return submission;
    }

    private SubmissionAnswerEntity requireAnswer(Long answerId) {
        SubmissionAnswerEntity answer = submissionAnswerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_ANSWER_NOT_FOUND", "提交答案不存在");
        }
        return answer;
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "任务不存在");
        }
        return assignment;
    }
}
