package com.aubb.server.modules.grading.application.appeal;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.grading.application.GradingApplicationService;
import com.aubb.server.modules.grading.application.GradingMetricsRecorder;
import com.aubb.server.modules.grading.application.ManualGradeResultView;
import com.aubb.server.modules.grading.domain.appeal.GradeAppealStatus;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealEntity;
import com.aubb.server.modules.grading.infrastructure.appeal.GradeAppealMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.ReadPathAuthorizationService;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerView;
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
public class GradeAppealApplicationService {

    private final GradeAppealMapper gradeAppealMapper;
    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final SubmissionAnswerApplicationService submissionAnswerApplicationService;
    private final UserMapper userMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final ReadPathAuthorizationService readPathAuthorizationService;
    private final GradingApplicationService gradingApplicationService;
    private final GradingMetricsRecorder gradingMetricsRecorder;
    private final AuditLogApplicationService auditLogApplicationService;
    private final NotificationDispatchService notificationDispatchService;

    @Transactional
    public GradeAppealView createAppeal(
            Long submissionId, Long answerId, String reason, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        if (!Objects.equals(submission.getSubmitterUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权发起该成绩申诉");
        }
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (!readPathAuthorizationService.canReadMySubmissionHistory(principal, submission, assignment)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权发起该成绩申诉");
        }
        if (assignment.getGradePublishedAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_NOT_OPEN", "成绩发布后才能发起申诉");
        }
        SubmissionAnswerEntity answer = requireAnswer(answerId);
        if (!Objects.equals(answer.getSubmissionId(), submissionId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_SCOPE_INVALID", "当前答案不属于指定提交");
        }
        SubmissionAnswerView answerView = loadAnswerView(submission, assignment, answerId);
        assertAppealSupportedQuestion(answerView);
        assertActiveAppealAbsent(answerId);
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_REASON_REQUIRED", "申诉原因不能为空");
        }

        GradeAppealEntity entity = new GradeAppealEntity();
        entity.setOfferingId(assignment.getOfferingId());
        entity.setTeachingClassId(submission.getTeachingClassId());
        entity.setAssignmentId(assignment.getId());
        entity.setSubmissionId(submission.getId());
        entity.setSubmissionAnswerId(answerId);
        entity.setStudentUserId(principal.getUserId());
        entity.setStatus(GradeAppealStatus.PENDING.name());
        entity.setAppealReason(reason.trim());
        gradeAppealMapper.insert(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.GRADE_APPEAL_CREATED,
                "GRADE_APPEAL",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "assignmentId", assignment.getId(),
                        "submissionId", submission.getId(),
                        "submissionAnswerId", answerId));
        gradingMetricsRecorder.recordAppealCreated();
        return toView(entity, assignment, answerView);
    }

    @Transactional(readOnly = true)
    public List<GradeAppealView> listAssignmentAppeals(
            Long assignmentId, String status, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanReadAppeals(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        GradeAppealStatus statusFilter = parseStatus(status);
        return gradeAppealMapper
                .selectList(Wrappers.<GradeAppealEntity>lambdaQuery()
                        .eq(GradeAppealEntity::getAssignmentId, assignmentId)
                        .eq(
                                statusFilter != null,
                                GradeAppealEntity::getStatus,
                                statusFilter == null ? null : statusFilter.name())
                        .orderByDesc(GradeAppealEntity::getCreatedAt)
                        .orderByDesc(GradeAppealEntity::getId))
                .stream()
                .map(appeal -> toView(appeal, assignment, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GradeAppealView> listMyAppeals(Long offeringId, String status, AuthenticatedUserPrincipal principal) {
        if (!readPathAuthorizationService.canReadMyAppealHistory(principal, offeringId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看成绩申诉");
        }
        GradeAppealStatus statusFilter = parseStatus(status);
        return gradeAppealMapper
                .selectList(Wrappers.<GradeAppealEntity>lambdaQuery()
                        .eq(GradeAppealEntity::getOfferingId, offeringId)
                        .eq(GradeAppealEntity::getStudentUserId, principal.getUserId())
                        .eq(
                                statusFilter != null,
                                GradeAppealEntity::getStatus,
                                statusFilter == null ? null : statusFilter.name())
                        .orderByDesc(GradeAppealEntity::getCreatedAt)
                        .orderByDesc(GradeAppealEntity::getId))
                .stream()
                .map(appeal -> toView(appeal, null, null))
                .toList();
    }

    @Transactional
    public GradeAppealView reviewAppeal(
            Long appealId,
            ReviewDecision decision,
            String responseText,
            Integer revisedScore,
            String revisedFeedbackText,
            AuthenticatedUserPrincipal principal) {
        GradeAppealEntity appeal = requireAppeal(appealId);
        AssignmentEntity assignment = requireAssignment(appeal.getAssignmentId());
        SubmissionEntity submission = requireSubmission(appeal.getSubmissionId());
        courseAuthorizationService.assertCanReviewAppeal(
                principal, assignment.getOfferingId(), submission.getTeachingClassId());
        GradeAppealStatus currentStatus = GradeAppealStatus.valueOf(appeal.getStatus());
        if (GradeAppealStatus.ACCEPTED.equals(currentStatus) || GradeAppealStatus.REJECTED.equals(currentStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_ALREADY_RESOLVED", "当前申诉已处理完成");
        }
        SubmissionAnswerEntity answer = requireAnswer(appeal.getSubmissionAnswerId());
        if (!Objects.equals(answer.getSubmissionId(), submission.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_SCOPE_INVALID", "申诉记录与提交答案不一致");
        }

        GradeAppealStatus nextStatus =
                switch (decision) {
                    case IN_REVIEW -> GradeAppealStatus.IN_REVIEW;
                    case ACCEPTED -> GradeAppealStatus.ACCEPTED;
                    case REJECTED -> GradeAppealStatus.REJECTED;
                };
        String normalizedResponse = StringUtils.hasText(responseText) ? responseText.trim() : null;
        appeal.setStatus(nextStatus.name());
        appeal.setResponseText(normalizedResponse);
        appeal.setRespondedByUserId(principal.getUserId());
        appeal.setRespondedAt(OffsetDateTime.now());

        SubmissionAnswerView answerView;
        if (ReviewDecision.ACCEPTED.equals(decision)) {
            if (revisedScore == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "GRADE_APPEAL_REVISED_SCORE_REQUIRED", "通过申诉时必须提供复核分数");
            }
            String feedbackForGrade =
                    StringUtils.hasText(revisedFeedbackText) ? revisedFeedbackText.trim() : answer.getFeedbackText();
            ManualGradeResultView gradeResult = gradingApplicationService.overrideAnswerGrade(
                    submission.getId(), answer.getId(), revisedScore, feedbackForGrade, principal);
            answerView = gradeResult.answer();
            appeal.setResolvedScore(answerView.finalScore());
        } else {
            answerView = loadAnswerView(submission, assignment, appeal.getSubmissionAnswerId());
            if (ReviewDecision.REJECTED.equals(decision)) {
                appeal.setResolvedScore(answerView.finalScore());
            }
        }
        gradeAppealMapper.updateById(appeal);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignmentId", assignment.getId());
        metadata.put("submissionId", submission.getId());
        metadata.put("submissionAnswerId", appeal.getSubmissionAnswerId());
        metadata.put("decision", nextStatus.name());
        if (appeal.getResolvedScore() != null) {
            metadata.put("resolvedScore", appeal.getResolvedScore());
        }
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.GRADE_APPEAL_REVIEWED,
                "GRADE_APPEAL",
                String.valueOf(appeal.getId()),
                AuditResult.SUCCESS,
                metadata);
        notificationDispatchService.notifyGradeAppealResolved(appeal, assignment, principal.getUserId());
        gradingMetricsRecorder.recordAppealReviewed(nextStatus);
        return toView(appeal, assignment, answerView);
    }

    private GradeAppealView toView(
            GradeAppealEntity appeal, AssignmentEntity assignmentOverride, SubmissionAnswerView answerViewOverride) {
        AssignmentEntity assignment =
                assignmentOverride == null ? requireAssignment(appeal.getAssignmentId()) : assignmentOverride;
        SubmissionEntity submission = requireSubmission(appeal.getSubmissionId());
        SubmissionAnswerView answerView = answerViewOverride == null
                ? loadAnswerView(submission, assignment, appeal.getSubmissionAnswerId())
                : answerViewOverride;
        UserEntity student = userMapper.selectById(appeal.getStudentUserId());
        return new GradeAppealView(
                appeal.getId(),
                appeal.getOfferingId(),
                appeal.getTeachingClassId(),
                appeal.getAssignmentId(),
                assignment.getTitle(),
                appeal.getSubmissionId(),
                appeal.getSubmissionAnswerId(),
                answerView.assignmentQuestionId(),
                answerView.questionTitle(),
                answerView.questionType(),
                appeal.getStudentUserId(),
                student == null ? null : student.getUsername(),
                student == null ? null : student.getDisplayName(),
                GradeAppealStatus.valueOf(appeal.getStatus()),
                appeal.getAppealReason(),
                appeal.getResponseText(),
                answerView.finalScore(),
                appeal.getResolvedScore(),
                answerView.feedbackText(),
                appeal.getRespondedByUserId(),
                appeal.getRespondedAt(),
                appeal.getCreatedAt(),
                appeal.getUpdatedAt());
    }

    private SubmissionAnswerView loadAnswerView(
            SubmissionEntity submission, AssignmentEntity assignment, Long answerId) {
        return submissionAnswerApplicationService.loadAnswerViews(submission.getId(), assignment.getId(), true).stream()
                .filter(answerView -> Objects.equals(answerView.id(), answerId))
                .findFirst()
                .orElseThrow(
                        () -> new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_ANSWER_NOT_FOUND", "提交答案不存在"));
    }

    private void assertAppealSupportedQuestion(SubmissionAnswerView answerView) {
        if (AssignmentQuestionType.SINGLE_CHOICE.equals(answerView.questionType())
                || AssignmentQuestionType.MULTIPLE_CHOICE.equals(answerView.questionType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_NOT_SUPPORTED", "当前仅支持非客观题成绩申诉");
        }
    }

    private void assertActiveAppealAbsent(Long answerId) {
        Long activeCount = gradeAppealMapper.selectCount(Wrappers.<GradeAppealEntity>lambdaQuery()
                .eq(GradeAppealEntity::getSubmissionAnswerId, answerId)
                .in(
                        GradeAppealEntity::getStatus,
                        List.of(GradeAppealStatus.PENDING.name(), GradeAppealStatus.IN_REVIEW.name())));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_ALREADY_OPEN", "当前答案已有处理中申诉");
        }
    }

    private GradeAppealStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return GradeAppealStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_STATUS_INVALID", "申诉状态非法");
        }
    }

    private GradeAppealEntity requireAppeal(Long appealId) {
        GradeAppealEntity appeal = gradeAppealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRADE_APPEAL_NOT_FOUND", "成绩申诉不存在");
        }
        return appeal;
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "任务不存在");
        }
        return assignment;
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

    public enum ReviewDecision {
        IN_REVIEW,
        ACCEPTED,
        REJECTED
    }
}
