package com.aubb.server.modules.judge.application;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.assignment.infrastructure.judge.AssignmentJudgeProfileMapper;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.judge.domain.JudgeJobStatus;
import com.aubb.server.modules.judge.domain.JudgeTriggerType;
import com.aubb.server.modules.judge.domain.JudgeVerdict;
import com.aubb.server.modules.judge.infrastructure.JudgeJobEntity;
import com.aubb.server.modules.judge.infrastructure.JudgeJobMapper;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JudgeApplicationService {

    private static final String ENGINE_CODE = "GO_JUDGE";

    private final JudgeJobMapper judgeJobMapper;
    private final SubmissionMapper submissionMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentJudgeProfileMapper assignmentJudgeProfileMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void enqueueAutoJudge(SubmissionEntity submission, AssignmentEntity assignment) {
        if (!isJudgeConfigured(assignment.getId())) {
            return;
        }
        createJudgeJob(submission, assignment, submission.getSubmitterUserId(), JudgeTriggerType.AUTO);
    }

    @Transactional(readOnly = true)
    public List<JudgeJobView> listMyJudgeJobs(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (!Objects.equals(submission.getSubmitterUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该评测任务");
        }
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该评测任务");
        }
        return listJobs(submissionId);
    }

    @Transactional(readOnly = true)
    public List<JudgeJobView> listTeacherJudgeJobs(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        return listJobs(submissionId);
    }

    @Transactional
    public JudgeJobView requeueJudge(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        assertJudgeConfigured(assignment.getId());
        return createJudgeJob(submission, assignment, principal.getUserId(), JudgeTriggerType.MANUAL_REJUDGE);
    }

    private JudgeJobView createJudgeJob(
            SubmissionEntity submission,
            AssignmentEntity assignment,
            Long requestedByUserId,
            JudgeTriggerType triggerType) {
        OffsetDateTime now = OffsetDateTime.now();
        JudgeJobEntity entity = new JudgeJobEntity();
        entity.setSubmissionId(submission.getId());
        entity.setAssignmentId(assignment.getId());
        entity.setOfferingId(submission.getOfferingId());
        entity.setTeachingClassId(submission.getTeachingClassId());
        entity.setSubmitterUserId(submission.getSubmitterUserId());
        entity.setRequestedByUserId(requestedByUserId);
        entity.setTriggerType(triggerType.name());
        entity.setStatus(JudgeJobStatus.PENDING.name());
        entity.setEngineCode(ENGINE_CODE);
        entity.setQueuedAt(now);
        judgeJobMapper.insert(entity);
        applicationEventPublisher.publishEvent(new JudgeExecutionRequestedEvent(entity.getId()));

        auditLogApplicationService.record(
                requestedByUserId,
                AuditAction.JUDGE_JOB_ENQUEUED,
                "JUDGE_JOB",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "submissionId", submission.getId(),
                        "assignmentId", assignment.getId(),
                        "triggerType", triggerType.name()));
        return toView(entity);
    }

    private List<JudgeJobView> listJobs(Long submissionId) {
        return judgeJobMapper
                .selectList(Wrappers.<JudgeJobEntity>lambdaQuery()
                        .eq(JudgeJobEntity::getSubmissionId, submissionId)
                        .orderByDesc(JudgeJobEntity::getQueuedAt)
                        .orderByDesc(JudgeJobEntity::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    private JudgeJobView toView(JudgeJobEntity entity) {
        return new JudgeJobView(
                entity.getId(),
                entity.getSubmissionId(),
                entity.getAssignmentId(),
                entity.getOfferingId(),
                entity.getTeachingClassId(),
                entity.getSubmitterUserId(),
                entity.getRequestedByUserId(),
                JudgeTriggerType.valueOf(entity.getTriggerType()),
                JudgeJobStatus.valueOf(entity.getStatus()),
                entity.getEngineCode(),
                entity.getEngineJobRef(),
                entity.getResultSummary(),
                entity.getVerdict() == null ? null : JudgeVerdict.valueOf(entity.getVerdict()),
                entity.getTotalCaseCount(),
                entity.getPassedCaseCount(),
                entity.getScore(),
                entity.getMaxScore(),
                entity.getStdoutExcerpt(),
                entity.getStderrExcerpt(),
                entity.getTimeMillis(),
                entity.getMemoryBytes(),
                entity.getErrorMessage(),
                entity.getQueuedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SubmissionEntity requireSubmission(Long submissionId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "提交不存在");
        }
        return submission;
    }

    private AssignmentEntity requireAssignment(Long assignmentId) {
        AssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "任务不存在");
        }
        return assignment;
    }

    private void assertJudgeConfigured(Long assignmentId) {
        if (!isJudgeConfigured(assignmentId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_NOT_CONFIGURED", "当前任务未配置自动评测");
        }
    }

    private boolean isJudgeConfigured(Long assignmentId) {
        return assignmentJudgeProfileMapper.selectById(assignmentId) != null;
    }
}
