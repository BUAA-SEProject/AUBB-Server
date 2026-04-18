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
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.grading.application.snapshot.GradePublishBatchDetailView;
import com.aubb.server.modules.grading.application.snapshot.GradePublishBatchSummaryView;
import com.aubb.server.modules.grading.application.snapshot.GradePublishSnapshotAnswerView;
import com.aubb.server.modules.grading.application.snapshot.GradePublishSnapshotPayloadView;
import com.aubb.server.modules.grading.application.snapshot.GradePublishSnapshotView;
import com.aubb.server.modules.grading.infrastructure.snapshot.GradePublishSnapshotBatchEntity;
import com.aubb.server.modules.grading.infrastructure.snapshot.GradePublishSnapshotBatchMapper;
import com.aubb.server.modules.grading.infrastructure.snapshot.GradePublishSnapshotEntity;
import com.aubb.server.modules.grading.infrastructure.snapshot.GradePublishSnapshotMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.notification.application.NotificationDispatchService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerView;
import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import com.aubb.server.modules.submission.domain.answer.SubmissionAnswerGradingStatus;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerEntity;
import com.aubb.server.modules.submission.infrastructure.answer.SubmissionAnswerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
    private final GradePublishSnapshotBatchMapper gradePublishSnapshotBatchMapper;
    private final GradePublishSnapshotMapper gradePublishSnapshotMapper;
    private final UserMapper userMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final PlatformTransactionManager transactionManager;
    private final NotificationDispatchService notificationDispatchService;
    private final GradingMetricsRecorder gradingMetricsRecorder;
    private final ObjectMapper objectMapper;

    @Transactional
    public ManualGradeResultView gradeAnswer(
            Long submissionId,
            Long answerId,
            Integer score,
            String feedbackText,
            AuthenticatedUserPrincipal principal) {
        return applyManualGrade(submissionId, answerId, score, feedbackText, principal, false);
    }

    @Transactional
    public ManualGradeResultView overrideAnswerGrade(
            Long submissionId,
            Long answerId,
            Integer score,
            String feedbackText,
            AuthenticatedUserPrincipal principal) {
        return applyManualGrade(submissionId, answerId, score, feedbackText, principal, true);
    }

    @Transactional
    public BatchManualGradeResultView batchGradeAnswers(
            Long assignmentId, List<BatchGradeItem> adjustments, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        if (adjustments == null || adjustments.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_BATCH_GRADE_REQUIRED", "批量调整必须至少包含一条成绩记录");
        }
        List<ManualGradeResultView> results = new ArrayList<>();
        for (BatchGradeItem adjustment : adjustments) {
            if (adjustment == null || adjustment.submissionId() == null || adjustment.answerId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SUBMISSION_BATCH_GRADE_INVALID", "批量调整项必须绑定提交和答案");
            }
            SubmissionEntity submission = requireSubmission(adjustment.submissionId());
            if (!Objects.equals(submission.getAssignmentId(), assignmentId)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "SUBMISSION_BATCH_GRADE_SCOPE_INVALID", "批量调整项存在不属于当前作业的提交");
            }
            results.add(gradeAnswer(
                    adjustment.submissionId(),
                    adjustment.answerId(),
                    adjustment.score(),
                    adjustment.feedbackText(),
                    principal));
        }
        return new BatchManualGradeResultView(assignmentId, results.size(), List.copyOf(results));
    }

    public GradeImportTemplateContent exportBatchGradeTemplate(
            Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        List<AssignmentQuestionSnapshot> questions =
                assignmentPaperApplicationService.loadQuestionSnapshots(assignmentId);
        Map<Long, AssignmentQuestionSnapshot> manualQuestions = questions.stream()
                .filter(this::supportsManualGrading)
                .collect(Collectors.toMap(
                        AssignmentQuestionSnapshot::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, Integer> questionOrder = new HashMap<>();
        int order = 0;
        for (AssignmentQuestionSnapshot question : questions) {
            questionOrder.put(question.id(), order++);
        }

        List<SubmissionEntity> submissions = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                .orderByAsc(SubmissionEntity::getSubmitterUserId)
                .orderByAsc(SubmissionEntity::getAttemptNo)
                .orderByAsc(SubmissionEntity::getSubmittedAt)
                .orderByAsc(SubmissionEntity::getId));
        if (submissions.isEmpty()) {
            return new GradeImportTemplateContent(
                    "assignment-grades-%s-template.csv".formatted(assignmentId),
                    "text/csv",
                    renderBatchGradeTemplate(List.of()).getBytes(StandardCharsets.UTF_8));
        }

        List<Long> submissionIds =
                submissions.stream().map(SubmissionEntity::getId).toList();
        Map<Long, SubmissionEntity> submissionById = submissions.stream()
                .collect(Collectors.toMap(SubmissionEntity::getId, Function.identity(), (left, right) -> left));
        Map<Long, UserEntity> usersById = userMapper
                .selectByIds(submissions.stream()
                        .map(SubmissionEntity::getSubmitterUserId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (left, right) -> left));
        List<SubmissionAnswerExportRow> rows = submissionAnswerMapper
                .selectList(Wrappers.<SubmissionAnswerEntity>lambdaQuery()
                        .in(SubmissionAnswerEntity::getSubmissionId, submissionIds))
                .stream()
                .filter(answer -> manualQuestions.containsKey(answer.getAssignmentQuestionId()))
                .sorted(Comparator.comparing((SubmissionAnswerEntity answer) ->
                                submissionById.get(answer.getSubmissionId()).getSubmitterUserId())
                        .thenComparing(answer ->
                                submissionById.get(answer.getSubmissionId()).getAttemptNo())
                        .thenComparing(answer ->
                                questionOrder.getOrDefault(answer.getAssignmentQuestionId(), Integer.MAX_VALUE))
                        .thenComparing(SubmissionAnswerEntity::getId))
                .map(answer -> {
                    SubmissionEntity submission = submissionById.get(answer.getSubmissionId());
                    UserEntity student = usersById.get(submission.getSubmitterUserId());
                    AssignmentQuestionSnapshot question = manualQuestions.get(answer.getAssignmentQuestionId());
                    return new SubmissionAnswerExportRow(
                            submission.getId(),
                            answer.getId(),
                            submission.getSubmissionNo(),
                            submission.getAttemptNo(),
                            student == null ? null : student.getUsername(),
                            student == null ? null : student.getDisplayName(),
                            question.title(),
                            question.questionType().name(),
                            question.score(),
                            answer.getFinalScore(),
                            answer.getFeedbackText());
                })
                .toList();
        return new GradeImportTemplateContent(
                "assignment-grades-%s-template.csv".formatted(assignmentId),
                "text/csv",
                renderBatchGradeTemplate(rows).getBytes(StandardCharsets.UTF_8));
    }

    public BatchGradeImportResultView importBatchGrades(
            Long assignmentId, MultipartFile file, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanGradeSubmission(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        TransactionTemplate rowTransaction = new TransactionTemplate(transactionManager);
        rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int total = 0;
        int success = 0;
        List<BatchGradeImportErrorView> errors = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_EMPTY", "导入文件不能为空");
            }
            BatchGradeImportHeader header = BatchGradeImportHeader.parse(headerLine);
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                total++;
                BatchGradeImportRow row = null;
                try {
                    row = header.readRow(parseCsvLine(line));
                    if (!row.hasAdjustment()) {
                        continue;
                    }
                    Integer score = row.score();
                    if (score == null) {
                        SubmissionAnswerEntity answer = requireAnswer(row.answerId());
                        score = answer.getFinalScore();
                        if (score == null) {
                            throw new BusinessException(
                                    HttpStatus.BAD_REQUEST,
                                    "SUBMISSION_BATCH_GRADE_SCORE_REQUIRED",
                                    "导入行必须提供分数，或答案已有最终分数");
                        }
                    }
                    Long submissionId = row.submissionId();
                    Long answerId = row.answerId();
                    String feedbackText = row.feedbackText();
                    Integer resolvedScore = score;
                    rowTransaction.executeWithoutResult(
                            _unused -> gradeAnswer(submissionId, answerId, resolvedScore, feedbackText, principal));
                    success++;
                } catch (BusinessException exception) {
                    errors.add(new BatchGradeImportErrorView(
                            (long) rowNumber,
                            row == null ? null : row.submissionId(),
                            row == null ? null : row.answerId(),
                            exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_READ_FAILED", "无法读取导入文件");
        }

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_GRADES_IMPORTED,
                "ASSIGNMENT",
                String.valueOf(assignmentId),
                errors.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                Map.of("total", total, "success", success, "failed", errors.size()));
        return new BatchGradeImportResultView(assignmentId, total, success, errors.size(), List.copyOf(errors));
    }

    @Transactional
    public AssignmentGradePublicationView publishAssignmentGrades(
            Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanPublishGrades(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        assertGradesReady(assignmentId);
        boolean initialPublication = assignment.getGradePublishedAt() == null;
        OffsetDateTime snapshotCapturedAt = currentDatabaseTimestamp();
        if (initialPublication) {
            assignment.setGradePublishedAt(snapshotCapturedAt);
            assignment.setGradePublishedByUserId(principal.getUserId());
            assignmentMapper.updateById(assignment);
            notificationDispatchService.notifyAssignmentGradesPublished(assignment, principal.getUserId());
        }
        GradePublishSnapshotBatchEntity snapshotBatch = captureGradePublishSnapshotBatch(
                assignment, principal.getUserId(), snapshotCapturedAt, initialPublication);
        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ASSIGNMENT_GRADES_PUBLISHED,
                "ASSIGNMENT",
                String.valueOf(assignmentId),
                AuditResult.SUCCESS,
                Map.of(
                        "offeringId", assignment.getOfferingId(),
                        "snapshotBatchId", snapshotBatch.getId(),
                        "publishSequence", snapshotBatch.getPublishSequence(),
                        "snapshotCount", snapshotBatch.getSnapshotCount(),
                        "initialPublication", initialPublication));
        gradingMetricsRecorder.recordGradePublication(initialPublication);
        return new AssignmentGradePublicationView(
                assignment.getId(),
                assignment.getGradePublishedByUserId(),
                assignment.getGradePublishedAt(),
                snapshotBatch.getId(),
                snapshotBatch.getPublishSequence(),
                snapshotBatch.getPublishedByUserId(),
                snapshotBatch.getPublishedAt(),
                snapshotBatch.getSnapshotCount(),
                initialPublication);
    }

    @Transactional(readOnly = true)
    public List<GradePublishBatchSummaryView> listGradePublishBatches(
            Long assignmentId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanPublishGrades(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        return gradePublishSnapshotBatchMapper
                .selectList(Wrappers.<GradePublishSnapshotBatchEntity>lambdaQuery()
                        .eq(GradePublishSnapshotBatchEntity::getAssignmentId, assignmentId)
                        .orderByDesc(GradePublishSnapshotBatchEntity::getPublishSequence)
                        .orderByDesc(GradePublishSnapshotBatchEntity::getId))
                .stream()
                .map(this::toBatchSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public GradePublishBatchDetailView getGradePublishBatch(
            Long assignmentId, Long batchId, AuthenticatedUserPrincipal principal) {
        AssignmentEntity assignment = requireAssignment(assignmentId);
        courseAuthorizationService.assertCanPublishGrades(
                principal, assignment.getOfferingId(), assignment.getTeachingClassId());
        GradePublishSnapshotBatchEntity batch = requireSnapshotBatch(batchId);
        if (!Objects.equals(batch.getAssignmentId(), assignmentId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRADE_PUBLISH_BATCH_SCOPE_INVALID", "快照批次不属于当前作业");
        }
        List<GradePublishSnapshotView> snapshots = gradePublishSnapshotMapper
                .selectList(Wrappers.<GradePublishSnapshotEntity>lambdaQuery()
                        .eq(GradePublishSnapshotEntity::getPublishBatchId, batchId)
                        .orderByAsc(GradePublishSnapshotEntity::getId))
                .stream()
                .map(this::toSnapshotView)
                .toList();
        return new GradePublishBatchDetailView(toBatchSummaryView(batch), snapshots);
    }

    private ManualGradeResultView applyManualGrade(
            Long submissionId,
            Long answerId,
            Integer score,
            String feedbackText,
            AuthenticatedUserPrincipal principal,
            boolean overrideMode) {
        SubmissionEntity submission = requireSubmission(submissionId);
        AssignmentEntity assignment = requireAssignment(submission.getAssignmentId());
        if (overrideMode) {
            courseAuthorizationService.assertCanOverrideGrade(
                    principal, assignment.getOfferingId(), submission.getTeachingClassId());
        } else {
            courseAuthorizationService.assertCanGradeSubmission(
                    principal, assignment.getOfferingId(), submission.getTeachingClassId());
        }

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
        metadata.put("overrideMode", overrideMode);
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

    private GradePublishSnapshotBatchEntity captureGradePublishSnapshotBatch(
            AssignmentEntity assignment, Long actorUserId, OffsetDateTime capturedAt, boolean initialPublication) {
        GradePublishSnapshotBatchEntity batch = new GradePublishSnapshotBatchEntity();
        batch.setAssignmentId(assignment.getId());
        batch.setOfferingId(assignment.getOfferingId());
        batch.setTeachingClassId(assignment.getTeachingClassId());
        batch.setPublishSequence(nextPublishSequence(assignment.getId()));
        batch.setSnapshotCount(0);
        batch.setInitialPublication(initialPublication);
        batch.setPublishedAt(capturedAt);
        batch.setPublishedByUserId(actorUserId);
        gradePublishSnapshotBatchMapper.insert(batch);

        List<SubmissionEntity> latestSubmissions = loadLatestSubmissionsForAssignment(assignment.getId());
        if (latestSubmissions.isEmpty()) {
            return batch;
        }

        Map<Long, UserEntity> userIndex = loadUsersByIds(latestSubmissions.stream()
                .map(SubmissionEntity::getSubmitterUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, TeachingClassEntity> teachingClassIndex = loadTeachingClassesByIds(latestSubmissions.stream()
                .map(SubmissionEntity::getTeachingClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        int snapshotCount = 0;
        for (SubmissionEntity submission : latestSubmissions) {
            SubmissionScoreSummaryView scoreSummary = submissionAnswerApplicationService.loadScoreSummary(
                    submission.getId(), assignment.getId(), true, true);
            List<SubmissionAnswerView> answers =
                    submissionAnswerApplicationService.loadAnswerViews(submission.getId(), assignment.getId(), true);
            UserEntity student = userIndex.get(submission.getSubmitterUserId());
            if (student == null) {
                continue;
            }
            TeachingClassEntity teachingClass = submission.getTeachingClassId() == null
                    ? null
                    : teachingClassIndex.get(submission.getTeachingClassId());
            GradePublishSnapshotPayloadView payload =
                    buildSnapshotPayload(assignment, submission, student, teachingClass, scoreSummary, answers);
            GradePublishSnapshotEntity snapshot = new GradePublishSnapshotEntity();
            snapshot.setPublishBatchId(batch.getId());
            snapshot.setAssignmentId(assignment.getId());
            snapshot.setOfferingId(assignment.getOfferingId());
            snapshot.setTeachingClassId(submission.getTeachingClassId());
            snapshot.setStudentUserId(student.getId());
            snapshot.setSubmissionId(submission.getId());
            snapshot.setSubmissionNo(submission.getSubmissionNo());
            snapshot.setAttemptNo(submission.getAttemptNo());
            snapshot.setSubmittedAt(submission.getSubmittedAt());
            snapshot.setTotalFinalScore(
                    scoreSummary == null || scoreSummary.finalScore() == null ? 0 : scoreSummary.finalScore());
            snapshot.setTotalMaxScore(
                    scoreSummary == null || scoreSummary.maxScore() == null ? 0 : scoreSummary.maxScore());
            snapshot.setAutoScoredScore(
                    scoreSummary == null || scoreSummary.autoScoredScore() == null
                            ? 0
                            : scoreSummary.autoScoredScore());
            snapshot.setManualScoredScore(scoreSummary == null ? null : scoreSummary.manualScoredScore());
            snapshot.setFullyGraded(scoreSummary != null && Boolean.TRUE.equals(scoreSummary.fullyGraded()));
            snapshot.setSnapshotJson(writeSnapshotPayload(payload));
            gradePublishSnapshotMapper.insert(snapshot);
            snapshotCount++;
        }
        batch.setSnapshotCount(snapshotCount);
        gradePublishSnapshotBatchMapper.updateById(batch);
        return batch;
    }

    private int nextPublishSequence(Long assignmentId) {
        return gradePublishSnapshotBatchMapper
                        .selectList(Wrappers.<GradePublishSnapshotBatchEntity>lambdaQuery()
                                .eq(GradePublishSnapshotBatchEntity::getAssignmentId, assignmentId)
                                .select(GradePublishSnapshotBatchEntity::getPublishSequence))
                        .stream()
                        .map(GradePublishSnapshotBatchEntity::getPublishSequence)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0)
                + 1;
    }

    private List<SubmissionEntity> loadLatestSubmissionsForAssignment(Long assignmentId) {
        List<SubmissionEntity> submissions = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .eq(SubmissionEntity::getAssignmentId, assignmentId)
                .orderByDesc(SubmissionEntity::getSubmittedAt)
                .orderByDesc(SubmissionEntity::getId));
        if (submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, SubmissionEntity> latestByStudent = new LinkedHashMap<>();
        for (SubmissionEntity submission : submissions) {
            latestByStudent.putIfAbsent(submission.getSubmitterUserId(), submission);
        }
        return List.copyOf(latestByStudent.values());
    }

    private Map<Long, UserEntity> loadUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(
                        UserEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, TeachingClassEntity> loadTeachingClassesByIds(Collection<Long> teachingClassIds) {
        if (teachingClassIds == null || teachingClassIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectByIds(teachingClassIds).stream()
                .collect(Collectors.toMap(
                        TeachingClassEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private GradePublishSnapshotPayloadView buildSnapshotPayload(
            AssignmentEntity assignment,
            SubmissionEntity submission,
            UserEntity student,
            TeachingClassEntity teachingClass,
            SubmissionScoreSummaryView scoreSummary,
            List<SubmissionAnswerView> answers) {
        List<GradePublishSnapshotAnswerView> answerSnapshots = answers.stream()
                .map(answer -> new GradePublishSnapshotAnswerView(
                        answer.id(),
                        answer.assignmentQuestionId(),
                        answer.questionTitle(),
                        answer.questionType(),
                        answer.autoScore(),
                        answer.manualScore(),
                        answer.finalScore(),
                        answer.gradingStatus(),
                        answer.feedbackText(),
                        answer.gradedByUserId(),
                        answer.gradedAt()))
                .toList();
        return new GradePublishSnapshotPayloadView(
                assignment.getId(),
                assignment.getOfferingId(),
                submission.getTeachingClassId(),
                new GradePublishSnapshotPayloadView.StudentView(
                        student.getId(),
                        student.getUsername(),
                        student.getDisplayName(),
                        submission.getTeachingClassId(),
                        teachingClass == null ? null : teachingClass.getClassCode(),
                        teachingClass == null ? null : teachingClass.getClassName()),
                new GradePublishSnapshotPayloadView.SubmissionView(
                        submission.getId(),
                        submission.getSubmissionNo(),
                        submission.getAttemptNo(),
                        submission.getSubmittedAt()),
                scoreSummary,
                answerSnapshots);
    }

    private String writeSnapshotPayload(GradePublishSnapshotPayloadView payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "GRADE_PUBLISH_SNAPSHOT_SERIALIZE_FAILED", "成绩发布快照无法序列化");
        }
    }

    private GradePublishSnapshotPayloadView readSnapshotPayload(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, GradePublishSnapshotPayloadView.class);
        } catch (JacksonException exception) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "GRADE_PUBLISH_SNAPSHOT_DESERIALIZE_FAILED", "成绩发布快照无法读取");
        }
    }

    private GradePublishSnapshotBatchEntity requireSnapshotBatch(Long batchId) {
        GradePublishSnapshotBatchEntity batch = gradePublishSnapshotBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRADE_PUBLISH_BATCH_NOT_FOUND", "成绩发布快照批次不存在");
        }
        return batch;
    }

    private GradePublishBatchSummaryView toBatchSummaryView(GradePublishSnapshotBatchEntity batch) {
        return new GradePublishBatchSummaryView(
                batch.getId(),
                batch.getAssignmentId(),
                batch.getOfferingId(),
                batch.getTeachingClassId(),
                batch.getPublishSequence(),
                batch.getSnapshotCount(),
                batch.getInitialPublication(),
                batch.getPublishedByUserId(),
                batch.getPublishedAt());
    }

    private GradePublishSnapshotView toSnapshotView(GradePublishSnapshotEntity entity) {
        GradePublishSnapshotPayloadView payload = readSnapshotPayload(entity.getSnapshotJson());
        GradePublishSnapshotPayloadView.StudentView student = payload.student();
        return new GradePublishSnapshotView(
                entity.getId(),
                entity.getStudentUserId(),
                student.username(),
                student.displayName(),
                student.teachingClassId(),
                student.teachingClassCode(),
                student.teachingClassName(),
                entity.getSubmissionId(),
                entity.getSubmissionNo(),
                entity.getAttemptNo(),
                entity.getSubmittedAt(),
                entity.getTotalFinalScore(),
                entity.getTotalMaxScore(),
                entity.getAutoScoredScore(),
                entity.getManualScoredScore(),
                entity.getFullyGraded(),
                payload);
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

    private boolean supportsManualGrading(AssignmentQuestionSnapshot question) {
        return !AssignmentQuestionType.SINGLE_CHOICE.equals(question.questionType())
                && !AssignmentQuestionType.MULTIPLE_CHOICE.equals(question.questionType());
    }

    private OffsetDateTime currentDatabaseTimestamp() {
        OffsetDateTime now = OffsetDateTime.now();
        return now.withNano((now.getNano() / 1_000) * 1_000);
    }

    private String renderBatchGradeTemplate(List<SubmissionAnswerExportRow> rows) {
        StringBuilder builder = new StringBuilder();
        appendCsvRow(
                builder,
                List.of(
                        "submissionId",
                        "answerId",
                        "submissionNo",
                        "attemptNo",
                        "studentUsername",
                        "studentDisplayName",
                        "questionTitle",
                        "questionType",
                        "maxScore",
                        "currentScore",
                        "currentFeedbackText",
                        "newScore",
                        "newFeedbackText"));
        for (SubmissionAnswerExportRow row : rows) {
            appendCsvRow(
                    builder,
                    List.of(
                            String.valueOf(row.submissionId()),
                            String.valueOf(row.answerId()),
                            defaultCsvValue(row.submissionNo()),
                            String.valueOf(row.attemptNo()),
                            defaultCsvValue(row.studentUsername()),
                            defaultCsvValue(row.studentDisplayName()),
                            defaultCsvValue(row.questionTitle()),
                            row.questionType(),
                            String.valueOf(row.maxScore()),
                            defaultCsvValue(row.currentScore()),
                            defaultCsvValue(row.currentFeedbackText()),
                            "",
                            ""));
        }
        return builder.toString();
    }

    private void appendCsvRow(StringBuilder builder, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(values.get(index)));
        }
        builder.append('\n');
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes =
                value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (currentChar == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        if (inQuotes) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_CSV_FORMAT_INVALID", "导入 CSV 存在未闭合引号");
        }
        columns.add(current.toString());
        return columns;
    }

    private String defaultCsvValue(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    public record BatchGradeItem(Long submissionId, Long answerId, Integer score, String feedbackText) {}

    private record SubmissionAnswerExportRow(
            Long submissionId,
            Long answerId,
            String submissionNo,
            Integer attemptNo,
            String studentUsername,
            String studentDisplayName,
            String questionTitle,
            String questionType,
            Integer maxScore,
            Integer currentScore,
            String currentFeedbackText) {}

    private record BatchGradeImportHeader(Map<String, Integer> columnIndexes) {

        private static final Set<String> REQUIRED_COLUMNS = Set.of("submissionid", "answerid");

        private static BatchGradeImportHeader parse(String headerLine) {
            List<String> columns = parseCsvLine(headerLine);
            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int index = 0; index < columns.size(); index++) {
                indexes.put(columns.get(index).trim().toLowerCase(Locale.ROOT), index);
            }
            for (String requiredColumn : REQUIRED_COLUMNS) {
                if (!indexes.containsKey(requiredColumn)) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "IMPORT_HEADER_INVALID", "导入文件缺少必需列: " + requiredColumn);
                }
            }
            if (!indexes.containsKey("newscore") && !indexes.containsKey("score")) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_HEADER_INVALID", "导入文件缺少必需列: newScore");
            }
            return new BatchGradeImportHeader(indexes);
        }

        private BatchGradeImportRow readRow(List<String> columns) {
            Long submissionId = parseLong(columns, "submissionid");
            Long answerId = parseLong(columns, "answerid");
            Integer score = parseInteger(columns, "newscore", "score");
            String feedbackText = parseString(columns, "newfeedbacktext", "feedbacktext");
            return new BatchGradeImportRow(submissionId, answerId, score, feedbackText);
        }

        private Long parseLong(List<String> columns, String columnName) {
            String value = parseString(columns, columnName);
            if (!StringUtils.hasText(value)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_ROW_INVALID", "导入行缺少 " + columnName);
            }
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IMPORT_ROW_INVALID", columnName + " 不是有效数字");
            }
        }

        private Integer parseInteger(List<String> columns, String... candidateNames) {
            String value = parseString(columns, candidateNames);
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "IMPORT_ROW_INVALID", candidateNames[0] + " 不是有效整数");
            }
        }

        private String parseString(List<String> columns, String... candidateNames) {
            for (String candidateName : candidateNames) {
                Integer index = columnIndexes.get(candidateName);
                if (index == null || index >= columns.size()) {
                    continue;
                }
                return columns.get(index).trim();
            }
            return null;
        }
    }

    private record BatchGradeImportRow(Long submissionId, Long answerId, Integer score, String feedbackText) {

        private boolean hasAdjustment() {
            return score != null || StringUtils.hasText(feedbackText);
        }
    }
}
