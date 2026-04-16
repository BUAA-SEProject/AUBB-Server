package com.aubb.server.modules.judge.application;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.application.paper.AssignmentPaperApplicationService;
import com.aubb.server.modules.assignment.application.paper.AssignmentQuestionSnapshot;
import com.aubb.server.modules.assignment.domain.question.AssignmentQuestionType;
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
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService.PersistedStructuredAnswers;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class JudgeApplicationService {

    private static final String ENGINE_CODE = "GO_JUDGE";

    private final JudgeJobMapper judgeJobMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper submissionAnswerMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentPaperApplicationService assignmentPaperApplicationService;
    private final AssignmentJudgeProfileMapper assignmentJudgeProfileMapper;
    private final CourseAuthorizationService courseAuthorizationService;
    private final AuditLogApplicationService auditLogApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueAutoJudge(SubmissionEntity submission, AssignmentEntity assignment) {
        if (!isJudgeConfigured(assignment.getId())) {
            return;
        }
        createJudgeJob(submission, assignment, null, submission.getSubmitterUserId(), JudgeTriggerType.AUTO);
    }

    @Transactional
    public void enqueueProgrammingJudges(
            SubmissionEntity submission, AssignmentEntity assignment, PersistedStructuredAnswers persistedAnswers) {
        for (SubmissionAnswerEntity answer : persistedAnswers.answers()) {
            AssignmentQuestionSnapshot question =
                    persistedAnswers.questionIndex().get(answer.getAssignmentQuestionId());
            if (question == null || !AssignmentQuestionType.PROGRAMMING.equals(question.questionType())) {
                continue;
            }
            createJudgeJob(submission, assignment, answer, submission.getSubmitterUserId(), JudgeTriggerType.AUTO);
        }
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

    @Transactional(readOnly = true)
    public List<JudgeJobView> listMyAnswerJudgeJobs(Long answerId, AuthenticatedUserPrincipal principal) {
        SubmissionAnswerEntity answer = requireAnswer(answerId);
        SubmissionEntity submission = requireSubmission(answer.getSubmissionId());
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (!Objects.equals(submission.getSubmitterUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该评测任务");
        }
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该评测任务");
        }
        return listAnswerJobs(answerId);
    }

    @Transactional(readOnly = true)
    public List<JudgeJobView> listTeacherAnswerJudgeJobs(Long answerId, AuthenticatedUserPrincipal principal) {
        SubmissionAnswerEntity answer = requireAnswer(answerId);
        SubmissionEntity submission = requireSubmission(answer.getSubmissionId());
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        return listAnswerJobs(answerId);
    }

    @Transactional(readOnly = true)
    public JudgeJobReportView getMyJudgeJobReport(Long judgeJobId, AuthenticatedUserPrincipal principal) {
        JudgeJobEntity judgeJob = requireJudgeJob(judgeJobId);
        SubmissionEntity submission = requireSubmission(judgeJob.getSubmissionId());
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (!Objects.equals(submission.getSubmitterUserId(), principal.getUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该评测报告");
        }
        if (!courseAuthorizationService.canViewAssignment(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该评测报告");
        }
        return toReportView(judgeJob, false);
    }

    @Transactional(readOnly = true)
    public JudgeJobReportView getTeacherJudgeJobReport(Long judgeJobId, AuthenticatedUserPrincipal principal) {
        JudgeJobEntity judgeJob = requireJudgeJob(judgeJobId);
        SubmissionEntity submission = requireSubmission(judgeJob.getSubmissionId());
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        return toReportView(judgeJob, true);
    }

    @Transactional
    public JudgeJobView requeueJudge(Long submissionId, AuthenticatedUserPrincipal principal) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        assertJudgeConfigured(assignment.getId());
        return createJudgeJob(submission, assignment, null, principal.getUserId(), JudgeTriggerType.MANUAL_REJUDGE);
    }

    @Transactional
    public JudgeJobView requeueAnswerJudge(Long answerId, AuthenticatedUserPrincipal principal) {
        SubmissionAnswerEntity answer = requireAnswer(answerId);
        SubmissionEntity submission = requireSubmission(answer.getSubmissionId());
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        courseAuthorizationService.assertCanManageAssignments(principal, assignment.getOfferingId());
        requireProgrammingQuestion(assignment.getId(), answer.getAssignmentQuestionId());
        return createJudgeJob(submission, assignment, answer, principal.getUserId(), JudgeTriggerType.MANUAL_REJUDGE);
    }

    private JudgeJobView createJudgeJob(
            SubmissionEntity submission,
            AssignmentEntity assignment,
            SubmissionAnswerEntity answer,
            Long requestedByUserId,
            JudgeTriggerType triggerType) {
        OffsetDateTime now = OffsetDateTime.now();
        JudgeJobEntity entity = new JudgeJobEntity();
        entity.setSubmissionId(submission.getId());
        entity.setSubmissionAnswerId(answer == null ? null : answer.getId());
        entity.setAssignmentId(assignment.getId());
        entity.setAssignmentQuestionId(answer == null ? null : answer.getAssignmentQuestionId());
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
                buildAuditMetadata(submission, assignment, answer, triggerType));
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

    private List<JudgeJobView> listAnswerJobs(Long answerId) {
        return judgeJobMapper
                .selectList(Wrappers.<JudgeJobEntity>lambdaQuery()
                        .eq(JudgeJobEntity::getSubmissionAnswerId, answerId)
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
                entity.getSubmissionAnswerId(),
                entity.getAssignmentId(),
                entity.getAssignmentQuestionId(),
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
                readCaseResults(entity.getCaseResultsJson()),
                entity.getDetailReportJson() != null
                        && !entity.getDetailReportJson().isBlank(),
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

    private JudgeJobEntity requireJudgeJob(Long judgeJobId) {
        JudgeJobEntity judgeJob = judgeJobMapper.selectById(judgeJobId);
        if (judgeJob == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "JUDGE_JOB_NOT_FOUND", "评测任务不存在");
        }
        return judgeJob;
    }

    private void assertJudgeConfigured(Long assignmentId) {
        if (!isJudgeConfigured(assignmentId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_JUDGE_NOT_CONFIGURED", "当前任务未配置自动评测");
        }
    }

    private boolean isJudgeConfigured(Long assignmentId) {
        return assignmentJudgeProfileMapper.selectById(assignmentId) != null;
    }

    private AssignmentQuestionSnapshot requireProgrammingQuestion(Long assignmentId, Long assignmentQuestionId) {
        return assignmentPaperApplicationService.loadQuestionSnapshots(assignmentId).stream()
                .filter(question -> Objects.equals(question.id(), assignmentQuestionId))
                .filter(question -> AssignmentQuestionType.PROGRAMMING.equals(question.questionType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST, "SUBMISSION_ANSWER_NOT_PROGRAMMING", "当前答案不支持重新评测"));
    }

    private List<JudgeJobCaseResultView> readCaseResults(String caseResultsJson) {
        if (caseResultsJson == null || caseResultsJson.isBlank()) {
            return List.of();
        }
        try {
            JudgeJobCaseResultView[] results = objectMapper.readValue(caseResultsJson, JudgeJobCaseResultView[].class);
            return results == null ? List.of() : List.of(results);
        } catch (JacksonException exception) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "JUDGE_JOB_CASE_RESULTS_BROKEN", "评测结果详情无法读取");
        }
    }

    private JudgeJobReportView toReportView(JudgeJobEntity entity, boolean revealSensitiveFields) {
        if (entity.getDetailReportJson() == null || entity.getDetailReportJson().isBlank()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "JUDGE_JOB_REPORT_NOT_READY", "当前评测任务尚未生成详细报告");
        }
        JudgeJobStoredReport storedReport = readDetailReport(entity.getDetailReportJson());
        List<JudgeJobCaseReportView> caseReports = revealSensitiveFields
                ? storedReport.caseReports()
                : storedReport.caseReports().stream()
                        .map(caseReport -> new JudgeJobCaseReportView(
                                caseReport.caseOrder(),
                                caseReport.verdict(),
                                caseReport.score(),
                                caseReport.maxScore(),
                                null,
                                null,
                                caseReport.stdoutText(),
                                caseReport.stderrText(),
                                caseReport.timeMillis(),
                                caseReport.memoryBytes(),
                                caseReport.errorMessage(),
                                caseReport.engineStatus(),
                                caseReport.exitStatus(),
                                caseReport.compileCommand(),
                                caseReport.runCommand()))
                        .toList();
        return new JudgeJobReportView(
                entity.getId(),
                entity.getSubmissionId(),
                entity.getSubmissionAnswerId(),
                entity.getAssignmentId(),
                entity.getAssignmentQuestionId(),
                JudgeJobStatus.valueOf(entity.getStatus()),
                entity.getVerdict() == null ? null : JudgeVerdict.valueOf(entity.getVerdict()),
                entity.getResultSummary(),
                entity.getErrorMessage(),
                storedReport.stdoutText(),
                storedReport.stderrText(),
                entity.getScore(),
                entity.getMaxScore(),
                entity.getPassedCaseCount(),
                entity.getTotalCaseCount(),
                entity.getTimeMillis(),
                entity.getMemoryBytes(),
                storedReport.executionMetadata(),
                caseReports,
                entity.getQueuedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt());
    }

    private JudgeJobStoredReport readDetailReport(String detailReportJson) {
        try {
            return objectMapper.readValue(detailReportJson, JudgeJobStoredReport.class);
        } catch (JacksonException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "JUDGE_JOB_REPORT_BROKEN", "评测详细报告无法读取");
        }
    }

    private Map<String, Object> buildAuditMetadata(
            SubmissionEntity submission,
            AssignmentEntity assignment,
            SubmissionAnswerEntity answer,
            JudgeTriggerType triggerType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submissionId", submission.getId());
        metadata.put("assignmentId", assignment.getId());
        metadata.put("submissionAnswerId", answer == null ? null : answer.getId());
        metadata.put("assignmentQuestionId", answer == null ? null : answer.getAssignmentQuestionId());
        metadata.put("triggerType", triggerType.name());
        return metadata;
    }
}
