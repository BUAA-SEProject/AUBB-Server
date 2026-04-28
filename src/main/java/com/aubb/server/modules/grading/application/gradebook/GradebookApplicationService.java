package com.aubb.server.modules.grading.application.gradebook;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionMapper;
import com.aubb.server.modules.audit.application.SensitiveOperationAuditService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceRef;
import com.aubb.server.modules.identityaccess.application.authz.core.AuthorizationResourceType;
import com.aubb.server.modules.identityaccess.application.authz.core.ReadPathAuthorizationService;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradebookApplicationService {

    private static final double PASS_SCORE_RATE = 0.6d;
    private static final List<String> HISTORY_READABLE_ROSTER_STATUSES = List.of(
            CourseMemberStatus.ACTIVE.name(),
            CourseMemberStatus.DROPPED.name(),
            CourseMemberStatus.TRANSFERRED.name(),
            CourseMemberStatus.COMPLETED.name());
    private static final List<ScoreBandDefinition> SCORE_BAND_DEFINITIONS = List.of(
            new ScoreBandDefinition("EXCELLENT", "优秀", 90, null, 0.9, 1.0000001),
            new ScoreBandDefinition("GOOD", "良好", 80, 90, 0.8, 0.9),
            new ScoreBandDefinition("MEDIUM", "中等", 70, 80, 0.7, 0.8),
            new ScoreBandDefinition("PASS", "及格", 60, 70, 0.6, 0.7),
            new ScoreBandDefinition("FAIL", "不及格", 0, 60, 0.0, 0.6));

    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final UserMapper userMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentSectionMapper assignmentSectionMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerApplicationService submissionAnswerApplicationService;
    private final ReadPathAuthorizationService readPathAuthorizationService;
    private final SensitiveOperationAuditService sensitiveOperationAuditService;
    private final GradebookQueryRepository gradebookQueryRepository;

    @Transactional(readOnly = true)
    public GradebookPageView getOfferingGradebook(
            Long offeringId,
            Long teachingClassId,
            Long studentUserId,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        TeachingClassEntity teachingClass = resolveTeachingClassScope(offeringId, teachingClassId);
        assertCanReadGradebook(
                principal, offeringId, teachingClass == null ? null : teachingClass.getId(), "当前用户无权查看成绩册");
        return buildGradebook(offering, teachingClass, studentUserId, page, pageSize);
    }

    @Transactional(readOnly = true)
    public GradebookPageView getTeachingClassGradebook(
            Long teachingClassId, Long studentUserId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        CourseOfferingEntity offering = requireOffering(teachingClass.getOfferingId());
        assertCanReadGradebook(principal, offering.getId(), teachingClassId, "当前用户无权查看成绩册");
        return buildGradebook(offering, teachingClass, studentUserId, page, pageSize);
    }

    @Transactional(readOnly = true)
    public GradebookExportContent exportOfferingGradebook(
            Long offeringId, Long teachingClassId, Long studentUserId, AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        TeachingClassEntity teachingClass = resolveTeachingClassScope(offeringId, teachingClassId);
        AuthorizationResourceRef resourceRef = teachingClass == null
                ? new AuthorizationResourceRef(AuthorizationResourceType.OFFERING, offeringId)
                : new AuthorizationResourceRef(AuthorizationResourceType.CLASS, teachingClass.getId());
        ReadPathAuthorizationService.TeachingReadScope scope =
                readPathAuthorizationService.resolveTeachingReadScope(principal, "grade.export", offeringId);
        boolean allowed = teachingClass == null ? scope.offeringReadable() : scope.canReadClass(teachingClass.getId());
        if (!allowed) {
            sensitiveOperationAuditService.recordDenied(
                    principal, AuditAction.GRADE_EXPORT, "grade.export", resourceRef, "DENY_SCOPE_FILTER", Map.of());
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权导出成绩册");
        }
        GradebookExportContent content = toCsvExport(buildGradebookSnapshot(offering, teachingClass, studentUserId));
        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        if (studentUserId != null) {
            auditMetadata.put("studentUserId", studentUserId);
        }
        sensitiveOperationAuditService.recordAllowed(
                principal, AuditAction.GRADE_EXPORT, "grade.export", resourceRef, auditMetadata);
        return content;
    }

    @Transactional(readOnly = true)
    public GradebookExportContent exportTeachingClassGradebook(
            Long teachingClassId, Long studentUserId, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        CourseOfferingEntity offering = requireOffering(teachingClass.getOfferingId());
        AuthorizationResourceRef resourceRef =
                new AuthorizationResourceRef(AuthorizationResourceType.CLASS, teachingClassId);
        ReadPathAuthorizationService.TeachingReadScope scope =
                readPathAuthorizationService.resolveTeachingReadScope(principal, "grade.export", offering.getId());
        if (!scope.canReadClass(teachingClassId)) {
            sensitiveOperationAuditService.recordDenied(
                    principal, AuditAction.GRADE_EXPORT, "grade.export", resourceRef, "DENY_SCOPE_FILTER", Map.of());
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权导出成绩册");
        }
        GradebookExportContent content = toCsvExport(buildGradebookSnapshot(offering, teachingClass, studentUserId));
        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        if (studentUserId != null) {
            auditMetadata.put("studentUserId", studentUserId);
        }
        sensitiveOperationAuditService.recordAllowed(
                principal, AuditAction.GRADE_EXPORT, "grade.export", resourceRef, auditMetadata);
        return content;
    }

    @Transactional(readOnly = true)
    public GradebookReportView getOfferingGradebookReport(
            Long offeringId, Long teachingClassId, Long studentUserId, AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        TeachingClassEntity teachingClass = resolveTeachingClassScope(offeringId, teachingClassId);
        assertCanReadGradebook(
                principal, offeringId, teachingClass == null ? null : teachingClass.getId(), "当前用户无权查看成绩报表");
        return buildGradebookReport(offering, teachingClass, studentUserId, teachingClass == null);
    }

    @Transactional(readOnly = true)
    public GradebookReportView getTeachingClassGradebookReport(
            Long teachingClassId, Long studentUserId, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        CourseOfferingEntity offering = requireOffering(teachingClass.getOfferingId());
        assertCanReadGradebook(principal, offering.getId(), teachingClassId, "当前用户无权查看成绩报表");
        return buildGradebookReport(offering, teachingClass, studentUserId, false);
    }

    @Transactional(readOnly = true)
    public StudentGradebookView getStudentGradebook(
            Long offeringId, Long studentUserId, AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        readPathAuthorizationService.assertCanReadStudentInOffering(
                principal, "grade.read", offeringId, studentUserId, "当前用户无权查看学生成绩册");
        return buildStudentGradebook(
                offering, studentUserId, true, true, HttpStatus.NOT_FOUND, "当前学生不在课程名册中", true, true);
    }

    private void assertCanReadGradebook(
            AuthenticatedUserPrincipal principal, Long offeringId, Long teachingClassId, String message) {
        ReadPathAuthorizationService.TeachingReadScope scope =
                readPathAuthorizationService.resolveTeachingReadScope(principal, "grade.read", offeringId);
        boolean allowed = teachingClassId == null ? scope.offeringReadable() : scope.canReadClass(teachingClassId);
        if (!allowed) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    @Transactional(readOnly = true)
    public StudentGradebookView getMyGradebook(Long offeringId, AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        return buildStudentGradebook(
                offering, principal.getUserId(), false, false, HttpStatus.FORBIDDEN, "当前用户无权查看学生成绩册", false, true);
    }

    @Transactional(readOnly = true)
    public GradebookExportContent exportMyGradebook(Long offeringId, AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        return toStudentCsvExport(buildStudentGradebook(
                offering, principal.getUserId(), false, false, HttpStatus.FORBIDDEN, "当前用户无权查看学生成绩册", false, true));
    }

    private StudentGradebookView buildStudentGradebook(
            CourseOfferingEntity offering,
            Long studentUserId,
            boolean revealNonObjectiveScores,
            boolean revealUnpublishedScores,
            HttpStatus missingStudentStatus,
            String missingStudentMessage,
            boolean includeRanking,
            boolean allowHistoricalMembership) {
        List<StudentRosterEntry> roster = loadRoster(offering.getId(), null, studentUserId, allowHistoricalMembership);
        StudentRosterEntry student = roster.stream()
                .filter(candidate -> Objects.equals(candidate.user().getId(), studentUserId))
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(missingStudentStatus, "COURSE_STUDENT_NOT_FOUND", missingStudentMessage));

        List<AssignmentEntity> assignments = loadStructuredAssignments(offering.getId(), null);
        Map<Long, Integer> assignmentMaxScores = loadAssignmentMaxScores(assignments);
        Map<Long, SubmissionEntity> latestSubmissions =
                loadLatestSubmissions(assignments, List.of(student.user().getId()));
        Map<Long, SubmissionScoreSummaryView> scoreSummaries =
                loadScoreSummaries(latestSubmissions.values(), assignments, revealNonObjectiveScores);
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClassIndex(assignments, roster);

        List<StudentGradebookView.AssignmentGradeView> assignmentViews = new ArrayList<>();
        int submittedCount = 0;
        int gradedCount = 0;
        int totalFinalScore = 0;
        int totalMaxScore = 0;
        int totalWeight = 0;
        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        for (AssignmentEntity assignment : assignments) {
            if (!isApplicable(assignment.getTeachingClassId(), student.teachingClassId())) {
                continue;
            }
            int assignmentMaxScore = assignmentMaxScores.getOrDefault(assignment.getId(), 0);
            int gradeWeight = defaultGradeWeight(assignment.getGradeWeight());
            totalMaxScore += assignmentMaxScore;
            totalWeight += gradeWeight;
            SubmissionEntity submission = latestSubmissions.get(
                    latestSubmissionKey(assignment.getId(), student.user().getId()));
            SubmissionScoreSummaryView summary = submission == null ? null : scoreSummaries.get(submission.getId());
            GradebookPageView.GradeCellView gradeCell =
                    toGradeCell(assignment, assignmentMaxScore, true, submission, summary, revealUnpublishedScores);
            if (Boolean.TRUE.equals(gradeCell.submitted())) {
                submittedCount++;
            }
            if (Boolean.TRUE.equals(gradeCell.fullyGraded())) {
                gradedCount++;
            }
            if (gradeCell.finalScore() != null) {
                totalFinalScore += gradeCell.finalScore();
            }
            if (gradeCell.weightedScore() != null) {
                totalWeightedScore = totalWeightedScore.add(BigDecimal.valueOf(gradeCell.weightedScore()));
            }
            assignmentViews.add(new StudentGradebookView.AssignmentGradeView(
                    toAssignmentColumn(assignment, assignmentMaxScore, classIndex.get(assignment.getTeachingClassId())),
                    gradeCell));
        }

        TeachingClassEntity studentClass = classIndex.get(student.teachingClassId());
        StudentRank studentRank = includeRanking ? loadStudentRank(offering, studentUserId) : StudentRank.EMPTY;
        return new StudentGradebookView(
                toScope(offering.getId(), studentClass),
                new StudentGradebookView.StudentView(
                        student.user().getId(),
                        student.user().getUsername(),
                        student.user().getDisplayName(),
                        student.teachingClassId(),
                        studentClass == null ? null : studentClass.getClassCode(),
                        studentClass == null ? null : studentClass.getClassName(),
                        studentRank.offeringRank(),
                        studentRank.teachingClassRank()),
                new StudentGradebookView.SummaryView(
                        assignmentViews.size(),
                        submittedCount,
                        gradedCount,
                        totalFinalScore,
                        totalMaxScore,
                        roundDecimal(totalWeightedScore, 2),
                        totalWeight,
                        ratio(totalWeightedScore.doubleValue(), totalWeight)),
                List.copyOf(assignmentViews));
    }

    private GradebookPageView buildGradebook(
            CourseOfferingEntity offering,
            TeachingClassEntity teachingClass,
            Long studentUserId,
            long page,
            long pageSize) {
        GradebookQueryRepository.GradebookPageAggregate aggregate = gradebookQueryRepository.loadOfferingPage(
                offering.getId(), teachingClass == null ? null : teachingClass.getId(), studentUserId, page, pageSize);
        return new GradebookPageView(
                aggregate.scope(),
                aggregate.summary(),
                aggregate.assignmentColumns(),
                aggregate.items(),
                aggregate.total(),
                page,
                pageSize);
    }

    private GradebookSnapshot buildGradebookSnapshot(
            CourseOfferingEntity offering, TeachingClassEntity teachingClass, Long studentUserId) {
        Long teachingClassId = teachingClass == null ? null : teachingClass.getId();
        List<AssignmentEntity> assignments = loadStructuredAssignments(offering.getId(), teachingClassId);
        List<StudentRosterEntry> roster = loadRoster(offering.getId(), teachingClassId, studentUserId, true);
        Map<Long, Integer> assignmentMaxScores = loadAssignmentMaxScores(assignments);
        List<Long> studentUserIds =
                roster.stream().map(entry -> entry.user().getId()).toList();
        Map<Long, SubmissionEntity> latestSubmissions = loadLatestSubmissions(assignments, studentUserIds);
        Map<Long, SubmissionScoreSummaryView> scoreSummaries =
                loadScoreSummaries(latestSubmissions.values(), assignments, true);
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClassIndex(assignments, roster);

        List<GradebookPageView.AssignmentColumnView> assignmentColumns = assignments.stream()
                .map(assignment -> toAssignmentColumn(
                        assignment,
                        assignmentMaxScores.getOrDefault(assignment.getId(), 0),
                        classIndex.get(assignment.getTeachingClassId())))
                .toList();

        List<GradebookPageView.StudentRowView> rows = roster.stream()
                .map(student -> toStudentRow(
                        student, assignments, assignmentMaxScores, latestSubmissions, scoreSummaries, classIndex))
                .toList();
        rows = applyRankings(rows);

        int submittedCount = rows.stream()
                .map(GradebookPageView.StudentRowView::submittedAssignmentCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int fullyGradedCount = rows.stream()
                .map(GradebookPageView.StudentRowView::gradedAssignmentCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int publishedCount = rows.stream()
                .flatMap(row -> row.grades().stream())
                .filter(cell -> Boolean.TRUE.equals(cell.submitted()) && Boolean.TRUE.equals(cell.gradePublished()))
                .mapToInt(ignored -> 1)
                .sum();
        return new GradebookSnapshot(
                toScope(offering.getId(), teachingClass),
                new GradebookPageView.SummaryView(
                        assignments.size(), roster.size(), submittedCount, fullyGradedCount, publishedCount),
                assignmentColumns,
                rows);
    }

    private GradebookExportContent toCsvExport(GradebookSnapshot snapshot) {
        String filename = snapshot.scope().teachingClassId() == null
                ? "gradebook-offering-%d.csv".formatted(snapshot.scope().offeringId())
                : "gradebook-class-%d.csv".formatted(snapshot.scope().teachingClassId());
        return new GradebookExportContent(
                filename, "text/csv", renderCsv(snapshot).getBytes(StandardCharsets.UTF_8));
    }

    private GradebookExportContent toStudentCsvExport(StudentGradebookView gradebookView) {
        return new GradebookExportContent(
                "gradebook-me-offering-%d.csv".formatted(gradebookView.scope().offeringId()),
                "text/csv",
                renderStudentCsv(gradebookView).getBytes(StandardCharsets.UTF_8));
    }

    private GradebookReportView buildGradebookReport(
            CourseOfferingEntity offering,
            TeachingClassEntity teachingClass,
            Long studentUserId,
            boolean includeTeachingClasses) {
        GradebookQueryRepository.GradebookReportAggregate aggregate = gradebookQueryRepository.loadOfferingReport(
                offering.getId(),
                teachingClass == null ? null : teachingClass.getId(),
                studentUserId,
                includeTeachingClasses);
        return new GradebookReportView(
                aggregate.scope(), aggregate.overview(), aggregate.assignments(), aggregate.teachingClasses());
    }

    private GradebookReportView.OverviewView buildOverview(GradebookSnapshot snapshot) {
        int applicableGradeCount = 0;
        int totalFinalScore = 0;
        int totalMaxScore = 0;
        int totalWeight = 0;
        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        int passedStudentCount = 0;
        for (GradebookPageView.StudentRowView row : snapshot.rows()) {
            totalFinalScore += row.totalFinalScore();
            totalMaxScore += row.totalMaxScore();
            totalWeight += defaultScore(row.totalWeight());
            totalWeightedScore = totalWeightedScore.add(BigDecimal.valueOf(row.totalWeightedScore()));
            if (isPassing(overallScoreRate(row))) {
                passedStudentCount++;
            }
            applicableGradeCount += (int) row.grades().stream()
                    .filter(cell -> Boolean.TRUE.equals(cell.applicable()))
                    .count();
        }
        return new GradebookReportView.OverviewView(
                snapshot.summary().assignmentCount(),
                snapshot.summary().studentCount(),
                applicableGradeCount,
                snapshot.summary().submittedCount(),
                snapshot.summary().fullyGradedCount(),
                snapshot.summary().publishedCount(),
                passedStudentCount,
                ratio(snapshot.summary().submittedCount(), applicableGradeCount),
                ratio(snapshot.summary().fullyGradedCount(), applicableGradeCount),
                ratio(snapshot.summary().publishedCount(), applicableGradeCount),
                ratio(passedStudentCount, snapshot.summary().studentCount()),
                average(totalFinalScore, snapshot.summary().studentCount()),
                ratio(totalFinalScore, totalMaxScore),
                average(totalWeightedScore.doubleValue(), snapshot.summary().studentCount()),
                ratio(totalWeightedScore.doubleValue(), totalWeight),
                buildScoreBands(
                        snapshot.rows().stream().map(this::overallScoreRate).toList(),
                        snapshot.summary().studentCount()));
    }

    private List<GradebookReportView.AssignmentStatView> buildAssignmentStats(GradebookSnapshot snapshot) {
        List<GradebookReportView.AssignmentStatView> stats = new ArrayList<>();
        for (int index = 0; index < snapshot.assignmentColumns().size(); index++) {
            GradebookPageView.AssignmentColumnView assignmentColumn =
                    snapshot.assignmentColumns().get(index);
            int applicableStudentCount = 0;
            int submittedStudentCount = 0;
            int fullyGradedStudentCount = 0;
            int publishedStudentCount = 0;
            int passedStudentCount = 0;
            int totalSubmittedFinalScore = 0;
            int totalSubmittedMaxScore = 0;
            BigDecimal totalSubmittedWeightedScore = BigDecimal.ZERO;
            List<Double> scoreRates = new ArrayList<>();
            for (GradebookPageView.StudentRowView row : snapshot.rows()) {
                GradebookPageView.GradeCellView cell = row.grades().get(index);
                if (!Boolean.TRUE.equals(cell.applicable())) {
                    continue;
                }
                applicableStudentCount++;
                if (Boolean.TRUE.equals(cell.submitted())) {
                    submittedStudentCount++;
                    totalSubmittedMaxScore += defaultScore(cell.maxScore());
                    totalSubmittedFinalScore += defaultScore(cell.finalScore());
                    if (cell.weightedScore() != null) {
                        totalSubmittedWeightedScore =
                                totalSubmittedWeightedScore.add(BigDecimal.valueOf(cell.weightedScore()));
                    }
                    double studentScoreRate = scoreRate(cell.finalScore(), cell.maxScore());
                    scoreRates.add(studentScoreRate);
                    if (isPassing(studentScoreRate)) {
                        passedStudentCount++;
                    }
                }
                if (Boolean.TRUE.equals(cell.fullyGraded())) {
                    fullyGradedStudentCount++;
                }
                if (Boolean.TRUE.equals(cell.submitted()) && Boolean.TRUE.equals(cell.gradePublished())) {
                    publishedStudentCount++;
                }
            }
            stats.add(new GradebookReportView.AssignmentStatView(
                    assignmentColumn.assignmentId(),
                    assignmentColumn.teachingClassId(),
                    assignmentColumn.teachingClassName(),
                    assignmentColumn.title(),
                    defaultScore(assignmentColumn.maxScore()),
                    defaultGradeWeight(assignmentColumn.gradeWeight()),
                    applicableStudentCount,
                    submittedStudentCount,
                    fullyGradedStudentCount,
                    publishedStudentCount,
                    passedStudentCount,
                    ratio(submittedStudentCount, applicableStudentCount),
                    ratio(fullyGradedStudentCount, applicableStudentCount),
                    ratio(publishedStudentCount, applicableStudentCount),
                    ratio(passedStudentCount, submittedStudentCount),
                    average(totalSubmittedFinalScore, submittedStudentCount),
                    ratio(totalSubmittedFinalScore, totalSubmittedMaxScore),
                    average(totalSubmittedWeightedScore.doubleValue(), submittedStudentCount),
                    buildScoreBands(scoreRates, submittedStudentCount)));
        }
        return List.copyOf(stats);
    }

    private List<GradebookReportView.TeachingClassStatView> buildTeachingClassStats(GradebookSnapshot snapshot) {
        Map<Long, MutableTeachingClassStat> stats = new LinkedHashMap<>();
        for (GradebookPageView.StudentRowView row : snapshot.rows()) {
            long key = row.teachingClassId() == null ? -1L : row.teachingClassId();
            MutableTeachingClassStat stat = stats.computeIfAbsent(
                    key,
                    ignored -> new MutableTeachingClassStat(
                            row.teachingClassId(), row.teachingClassCode(), row.teachingClassName()));
            stat.studentCount++;
            stat.applicableAssignmentCount += (int) row.grades().stream()
                    .filter(cell -> Boolean.TRUE.equals(cell.applicable()))
                    .count();
            stat.submittedAssignmentCount += defaultScore(row.submittedAssignmentCount());
            stat.gradedAssignmentCount += defaultScore(row.gradedAssignmentCount());
            stat.publishedAssignmentCount += (int) row.grades().stream()
                    .filter(cell -> Boolean.TRUE.equals(cell.submitted()) && Boolean.TRUE.equals(cell.gradePublished()))
                    .count();
            if (isPassing(overallScoreRate(row))) {
                stat.passedStudentCount++;
            }
            stat.totalFinalScore += defaultScore(row.totalFinalScore());
            stat.totalMaxScore += defaultScore(row.totalMaxScore());
            stat.totalWeight += defaultScore(row.totalWeight());
            stat.totalWeightedScore = stat.totalWeightedScore.add(BigDecimal.valueOf(row.totalWeightedScore()));
            stat.scoreRates.add(overallScoreRate(row));
        }
        return stats.values().stream()
                .map(stat -> new GradebookReportView.TeachingClassStatView(
                        stat.teachingClassId,
                        stat.teachingClassCode,
                        stat.teachingClassName,
                        stat.studentCount,
                        stat.applicableAssignmentCount,
                        stat.submittedAssignmentCount,
                        stat.gradedAssignmentCount,
                        stat.publishedAssignmentCount,
                        stat.passedStudentCount,
                        ratio(stat.submittedAssignmentCount, stat.applicableAssignmentCount),
                        ratio(stat.gradedAssignmentCount, stat.applicableAssignmentCount),
                        ratio(stat.publishedAssignmentCount, stat.applicableAssignmentCount),
                        ratio(stat.passedStudentCount, stat.studentCount),
                        average(stat.totalFinalScore, stat.studentCount),
                        ratio(stat.totalFinalScore, stat.totalMaxScore),
                        average(stat.totalWeightedScore.doubleValue(), stat.studentCount),
                        ratio(stat.totalWeightedScore.doubleValue(), stat.totalWeight),
                        buildScoreBands(stat.scoreRates, stat.studentCount)))
                .toList();
    }

    private String renderCsv(GradebookSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        List<String> header = new ArrayList<>(List.of(
                "username",
                "displayName",
                "teachingClassCode",
                "teachingClassName",
                "totalFinalScore",
                "totalMaxScore",
                "totalWeightedScore",
                "totalWeight",
                "weightedScoreRate",
                "offeringRank",
                "teachingClassRank",
                "submittedAssignmentCount",
                "gradedAssignmentCount"));
        for (GradebookPageView.AssignmentColumnView assignmentColumn : snapshot.assignmentColumns()) {
            String label = assignmentExportLabel(assignmentColumn);
            header.add(label + "-applicable");
            header.add(label + "-submitted");
            header.add(label + "-latestAttemptNo");
            header.add(label + "-submittedAt");
            header.add(label + "-finalScore");
            header.add(label + "-maxScore");
            header.add(label + "-gradeWeight");
            header.add(label + "-weightedScore");
            header.add(label + "-fullyGraded");
            header.add(label + "-gradePublished");
        }
        appendCsvRow(builder, header);

        for (GradebookPageView.StudentRowView row : snapshot.rows()) {
            List<String> record = new ArrayList<>();
            record.add(row.username());
            record.add(row.displayName());
            record.add(row.teachingClassCode());
            record.add(row.teachingClassName());
            record.add(String.valueOf(row.totalFinalScore()));
            record.add(String.valueOf(row.totalMaxScore()));
            record.add(String.valueOf(row.totalWeightedScore()));
            record.add(String.valueOf(row.totalWeight()));
            record.add(String.valueOf(row.weightedScoreRate()));
            record.add(row.offeringRank() == null ? null : String.valueOf(row.offeringRank()));
            record.add(row.teachingClassRank() == null ? null : String.valueOf(row.teachingClassRank()));
            record.add(String.valueOf(row.submittedAssignmentCount()));
            record.add(String.valueOf(row.gradedAssignmentCount()));
            for (int index = 0; index < row.grades().size(); index++) {
                GradebookPageView.GradeCellView cell = row.grades().get(index);
                GradebookPageView.AssignmentColumnView assignmentColumn =
                        snapshot.assignmentColumns().get(index);
                record.add(String.valueOf(Boolean.TRUE.equals(cell.applicable())));
                record.add(String.valueOf(Boolean.TRUE.equals(cell.submitted())));
                record.add(cell.latestAttemptNo() == null ? null : String.valueOf(cell.latestAttemptNo()));
                record.add(
                        cell.submittedAt() == null ? null : cell.submittedAt().toString());
                record.add(cell.finalScore() == null ? null : String.valueOf(cell.finalScore()));
                record.add(cell.maxScore() == null ? null : String.valueOf(cell.maxScore()));
                record.add(String.valueOf(defaultGradeWeight(assignmentColumn.gradeWeight())));
                record.add(cell.weightedScore() == null ? null : String.valueOf(cell.weightedScore()));
                record.add(cell.fullyGraded() == null ? null : String.valueOf(cell.fullyGraded()));
                record.add(String.valueOf(Boolean.TRUE.equals(cell.gradePublished())));
            }
            appendCsvRow(builder, record);
        }
        return builder.toString();
    }

    private String renderStudentCsv(StudentGradebookView gradebookView) {
        StringBuilder builder = new StringBuilder();
        appendCsvRow(
                builder,
                List.of(
                        "userId",
                        "username",
                        "displayName",
                        "teachingClassCode",
                        "teachingClassName",
                        "assignmentCount",
                        "submittedCount",
                        "gradedCount",
                        "totalFinalScore",
                        "totalMaxScore",
                        "totalWeightedScore",
                        "totalWeight",
                        "weightedScoreRate"));
        List<String> summaryRow = new ArrayList<>();
        summaryRow.add(String.valueOf(gradebookView.student().userId()));
        summaryRow.add(gradebookView.student().username());
        summaryRow.add(gradebookView.student().displayName());
        summaryRow.add(gradebookView.student().teachingClassCode());
        summaryRow.add(gradebookView.student().teachingClassName());
        summaryRow.add(String.valueOf(gradebookView.summary().assignmentCount()));
        summaryRow.add(String.valueOf(gradebookView.summary().submittedCount()));
        summaryRow.add(String.valueOf(gradebookView.summary().gradedCount()));
        summaryRow.add(String.valueOf(gradebookView.summary().totalFinalScore()));
        summaryRow.add(String.valueOf(gradebookView.summary().totalMaxScore()));
        summaryRow.add(String.valueOf(gradebookView.summary().totalWeightedScore()));
        summaryRow.add(String.valueOf(gradebookView.summary().totalWeight()));
        summaryRow.add(String.valueOf(gradebookView.summary().weightedScoreRate()));
        appendCsvRow(builder, summaryRow);
        builder.append('\n');

        appendCsvRow(
                builder,
                List.of(
                        "assignmentId",
                        "title",
                        "teachingClassCode",
                        "teachingClassName",
                        "maxScore",
                        "gradeWeight",
                        "gradePublished",
                        "applicable",
                        "submitted",
                        "latestAttemptNo",
                        "submittedAt",
                        "finalScore",
                        "weightedScore",
                        "fullyGraded"));
        for (StudentGradebookView.AssignmentGradeView assignmentView : gradebookView.assignments()) {
            GradebookPageView.AssignmentColumnView assignment = assignmentView.assignment();
            GradebookPageView.GradeCellView grade = assignmentView.grade();
            List<String> assignmentRow = new ArrayList<>();
            assignmentRow.add(String.valueOf(assignment.assignmentId()));
            assignmentRow.add(assignment.title());
            assignmentRow.add(
                    assignment.teachingClassId() == null
                            ? null
                            : gradebookView.student().teachingClassCode());
            assignmentRow.add(assignment.teachingClassName());
            assignmentRow.add(assignment.maxScore() == null ? null : String.valueOf(assignment.maxScore()));
            assignmentRow.add(String.valueOf(defaultGradeWeight(assignment.gradeWeight())));
            assignmentRow.add(String.valueOf(Boolean.TRUE.equals(assignment.gradePublished())));
            assignmentRow.add(String.valueOf(Boolean.TRUE.equals(grade.applicable())));
            assignmentRow.add(String.valueOf(Boolean.TRUE.equals(grade.submitted())));
            assignmentRow.add(grade.latestAttemptNo() == null ? null : String.valueOf(grade.latestAttemptNo()));
            assignmentRow.add(
                    grade.submittedAt() == null ? null : grade.submittedAt().toString());
            assignmentRow.add(grade.finalScore() == null ? null : String.valueOf(grade.finalScore()));
            assignmentRow.add(grade.weightedScore() == null ? null : String.valueOf(grade.weightedScore()));
            assignmentRow.add(grade.fullyGraded() == null ? null : String.valueOf(grade.fullyGraded()));
            appendCsvRow(builder, assignmentRow);
        }
        return builder.toString();
    }

    private String assignmentExportLabel(GradebookPageView.AssignmentColumnView assignmentColumn) {
        return assignmentColumn.teachingClassName() == null
                ? assignmentColumn.title()
                : "%s [%s]".formatted(assignmentColumn.title(), assignmentColumn.teachingClassName());
    }

    private void appendCsvRow(StringBuilder builder, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(values.get(i)));
        }
        builder.append('\n');
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes =
                value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private GradebookPageView.StudentRowView toStudentRow(
            StudentRosterEntry student,
            List<AssignmentEntity> assignments,
            Map<Long, Integer> assignmentMaxScores,
            Map<Long, SubmissionEntity> latestSubmissions,
            Map<Long, SubmissionScoreSummaryView> scoreSummaries,
            Map<Long, TeachingClassEntity> classIndex) {
        List<GradebookPageView.GradeCellView> grades = new ArrayList<>();
        int totalFinalScore = 0;
        int totalMaxScore = 0;
        int totalWeight = 0;
        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        int submittedAssignmentCount = 0;
        int gradedAssignmentCount = 0;
        for (AssignmentEntity assignment : assignments) {
            int assignmentMaxScore = assignmentMaxScores.getOrDefault(assignment.getId(), 0);
            boolean applicable = isApplicable(assignment.getTeachingClassId(), student.teachingClassId());
            if (applicable) {
                totalMaxScore += assignmentMaxScore;
                totalWeight += defaultGradeWeight(assignment.getGradeWeight());
            }
            SubmissionEntity submission = latestSubmissions.get(
                    latestSubmissionKey(assignment.getId(), student.user().getId()));
            SubmissionScoreSummaryView summary = submission == null ? null : scoreSummaries.get(submission.getId());
            GradebookPageView.GradeCellView cell =
                    toGradeCell(assignment, assignmentMaxScore, applicable, submission, summary, true);
            if (applicable && Boolean.TRUE.equals(cell.submitted())) {
                submittedAssignmentCount++;
            }
            if (applicable && Boolean.TRUE.equals(cell.fullyGraded())) {
                gradedAssignmentCount++;
            }
            if (applicable && cell.finalScore() != null) {
                totalFinalScore += cell.finalScore();
            }
            if (applicable && cell.weightedScore() != null) {
                totalWeightedScore = totalWeightedScore.add(BigDecimal.valueOf(cell.weightedScore()));
            }
            grades.add(cell);
        }

        TeachingClassEntity studentClass = classIndex.get(student.teachingClassId());
        return new GradebookPageView.StudentRowView(
                student.user().getId(),
                student.user().getUsername(),
                student.user().getDisplayName(),
                student.teachingClassId(),
                studentClass == null ? null : studentClass.getClassCode(),
                studentClass == null ? null : studentClass.getClassName(),
                totalFinalScore,
                totalMaxScore,
                roundDecimal(totalWeightedScore, 2),
                totalWeight,
                ratio(totalWeightedScore.doubleValue(), totalWeight),
                null,
                null,
                submittedAssignmentCount,
                gradedAssignmentCount,
                List.copyOf(grades));
    }

    private StudentRank loadStudentRank(CourseOfferingEntity offering, Long studentUserId) {
        return buildGradebookSnapshot(offering, null, null).rows().stream()
                .filter(row -> Objects.equals(row.userId(), studentUserId))
                .findFirst()
                .map(row -> new StudentRank(row.offeringRank(), row.teachingClassRank()))
                .orElse(StudentRank.EMPTY);
    }

    private List<GradebookPageView.StudentRowView> applyRankings(List<GradebookPageView.StudentRowView> rows) {
        Map<Long, Integer> offeringRanks = buildRankIndex(rows);
        Map<Long, Map<Long, Integer>> classRanks = new LinkedHashMap<>();
        rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> row.teachingClassId() == null ? -1L : row.teachingClassId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .forEach((teachingClassId, classRows) -> classRanks.put(teachingClassId, buildRankIndex(classRows)));
        return rows.stream()
                .map(row -> {
                    Long teachingClassKey = row.teachingClassId() == null ? -1L : row.teachingClassId();
                    return new GradebookPageView.StudentRowView(
                            row.userId(),
                            row.username(),
                            row.displayName(),
                            row.teachingClassId(),
                            row.teachingClassCode(),
                            row.teachingClassName(),
                            row.totalFinalScore(),
                            row.totalMaxScore(),
                            row.totalWeightedScore(),
                            row.totalWeight(),
                            row.weightedScoreRate(),
                            offeringRanks.get(row.userId()),
                            classRanks.getOrDefault(teachingClassKey, Map.of()).get(row.userId()),
                            row.submittedAssignmentCount(),
                            row.gradedAssignmentCount(),
                            row.grades());
                })
                .toList();
    }

    private Map<Long, Integer> buildRankIndex(List<GradebookPageView.StudentRowView> rows) {
        List<GradebookPageView.StudentRowView> sortedRows = rows.stream()
                .sorted(Comparator.comparingDouble(this::overallScoreRate)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(
                                        (GradebookPageView.StudentRowView row) -> defaultScore(row.totalFinalScore()))
                                .reversed())
                        .thenComparing(Comparator.comparingDouble(GradebookPageView.StudentRowView::totalWeightedScore)
                                .reversed())
                        .thenComparing(row -> row.username() == null ? "" : row.username())
                        .thenComparing(row -> row.userId() == null ? Long.MAX_VALUE : row.userId()))
                .toList();
        Map<Long, Integer> rankByUserId = new LinkedHashMap<>();
        GradebookPageView.StudentRowView previous = null;
        int currentRank = 0;
        for (int index = 0; index < sortedRows.size(); index++) {
            GradebookPageView.StudentRowView row = sortedRows.get(index);
            if (previous == null || !sameRank(previous, row)) {
                currentRank = index + 1;
            }
            rankByUserId.put(row.userId(), currentRank);
            previous = row;
        }
        return rankByUserId;
    }

    private boolean sameRank(GradebookPageView.StudentRowView left, GradebookPageView.StudentRowView right) {
        return Double.compare(overallScoreRate(left), overallScoreRate(right)) == 0
                && defaultScore(left.totalFinalScore()) == defaultScore(right.totalFinalScore())
                && Double.compare(left.totalWeightedScore(), right.totalWeightedScore()) == 0;
    }

    private GradebookPageView.GradeCellView toGradeCell(
            AssignmentEntity assignment,
            int assignmentMaxScore,
            boolean applicable,
            SubmissionEntity submission,
            SubmissionScoreSummaryView summary,
            boolean revealUnpublishedScores) {
        if (!applicable) {
            return new GradebookPageView.GradeCellView(
                    assignment.getId(), false, false, null, null, null, null, null, null, null, null, null);
        }
        boolean submitted = submission != null;
        boolean gradePublished = summary == null
                ? assignment.getGradePublishedAt() != null
                : Boolean.TRUE.equals(summary.gradePublished());
        boolean revealScores = revealUnpublishedScores || gradePublished;
        Integer finalScore = revealScores || summary == null ? summary == null ? null : summary.finalScore() : null;
        Double weightedScore = finalScore == null
                ? null
                : weightedScore(finalScore, assignmentMaxScore, defaultGradeWeight(assignment.getGradeWeight()));
        return new GradebookPageView.GradeCellView(
                assignment.getId(),
                true,
                submitted,
                submitted ? submission.getId() : null,
                submitted ? submission.getAttemptNo() : null,
                submitted ? submission.getSubmittedAt() : null,
                revealScores ? summary : null,
                finalScore,
                assignmentMaxScore,
                weightedScore,
                summary == null ? null : summary.fullyGraded(),
                gradePublished);
    }

    private GradebookPageView.AssignmentColumnView toAssignmentColumn(
            AssignmentEntity assignment, int maxScore, TeachingClassEntity teachingClass) {
        return new GradebookPageView.AssignmentColumnView(
                assignment.getId(),
                assignment.getTeachingClassId(),
                teachingClass == null ? null : teachingClass.getClassName(),
                assignment.getTitle(),
                assignment.getStatus(),
                assignment.getOpenAt(),
                assignment.getDueAt(),
                maxScore,
                defaultGradeWeight(assignment.getGradeWeight()),
                assignment.getGradePublishedAt() != null);
    }

    private GradebookPageView.ScopeView toScope(Long offeringId, TeachingClassEntity teachingClass) {
        return new GradebookPageView.ScopeView(
                offeringId,
                teachingClass == null ? null : teachingClass.getId(),
                teachingClass == null ? null : teachingClass.getClassCode(),
                teachingClass == null ? null : teachingClass.getClassName());
    }

    private List<AssignmentEntity> loadStructuredAssignments(Long offeringId, Long teachingClassId) {
        List<AssignmentEntity> assignments = assignmentMapper.selectList(Wrappers.<AssignmentEntity>lambdaQuery()
                .eq(AssignmentEntity::getOfferingId, offeringId)
                .ne(AssignmentEntity::getStatus, AssignmentStatus.DRAFT.name())
                .orderByAsc(AssignmentEntity::getOpenAt)
                .orderByAsc(AssignmentEntity::getId));
        if (teachingClassId != null) {
            assignments = assignments.stream()
                    .filter(assignment -> assignment.getTeachingClassId() == null
                            || Objects.equals(assignment.getTeachingClassId(), teachingClassId))
                    .toList();
        }
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<Long> structuredAssignmentIds = assignmentSectionMapper
                .selectList(Wrappers.<AssignmentSectionEntity>lambdaQuery()
                        .in(
                                AssignmentSectionEntity::getAssignmentId,
                                assignments.stream()
                                        .map(AssignmentEntity::getId)
                                        .toList())
                        .select(AssignmentSectionEntity::getAssignmentId))
                .stream()
                .map(AssignmentSectionEntity::getAssignmentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return assignments.stream()
                .filter(assignment -> structuredAssignmentIds.contains(assignment.getId()))
                .toList();
    }

    private Map<Long, Integer> loadAssignmentMaxScores(List<AssignmentEntity> assignments) {
        if (assignments.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> scoreByAssignmentId = new LinkedHashMap<>();
        assignmentSectionMapper
                .selectList(Wrappers.<AssignmentSectionEntity>lambdaQuery()
                        .in(
                                AssignmentSectionEntity::getAssignmentId,
                                assignments.stream()
                                        .map(AssignmentEntity::getId)
                                        .toList())
                        .orderByAsc(AssignmentSectionEntity::getAssignmentId)
                        .orderByAsc(AssignmentSectionEntity::getSectionOrder))
                .forEach(section -> scoreByAssignmentId.merge(
                        section.getAssignmentId(), defaultScore(section.getTotalScore()), Integer::sum));
        return scoreByAssignmentId;
    }

    private List<StudentRosterEntry> loadRoster(
            Long offeringId, Long teachingClassId, Long studentUserId, boolean allowHistoricalMembership) {
        List<String> readableStatuses = allowHistoricalMembership
                ? HISTORY_READABLE_ROSTER_STATUSES
                : List.of(CourseMemberStatus.ACTIVE.name());
        List<CourseMemberEntity> members = courseMemberMapper.selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getOfferingId, offeringId)
                .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.STUDENT.name())
                .in(CourseMemberEntity::getMemberStatus, readableStatuses));
        if (members.isEmpty()) {
            return List.of();
        }
        members = members.stream()
                .sorted(Comparator.comparing(GradebookApplicationService::isHistoricalRosterMember)
                        .thenComparing(GradebookApplicationService::hasMissingTeachingClass, Comparator.naturalOrder())
                        .thenComparing(
                                GradebookApplicationService::rosterMembershipRecency,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseMemberEntity::getId, Comparator.reverseOrder()))
                .toList();
        Map<Long, CourseMemberEntity> rosterByUserId = new LinkedHashMap<>();
        for (CourseMemberEntity member : members) {
            if (studentUserId != null && !Objects.equals(member.getUserId(), studentUserId)) {
                continue;
            }
            if (!rosterByUserId.containsKey(member.getUserId())) {
                rosterByUserId.put(member.getUserId(), member);
            }
        }
        if (rosterByUserId.isEmpty()) {
            return List.of();
        }
        List<CourseMemberEntity> rosterMembers = rosterByUserId.values().stream()
                .filter(member ->
                        teachingClassId == null || Objects.equals(member.getTeachingClassId(), teachingClassId))
                .toList();
        if (rosterMembers.isEmpty()) {
            return List.of();
        }
        Map<Long, UserEntity> userIndex =
                userMapper
                        .selectByIds(rosterMembers.stream()
                                .map(CourseMemberEntity::getUserId)
                                .toList())
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                UserEntity::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
        Map<Long, TeachingClassEntity> teachingClassIndex = loadTeachingClassIndex(rosterMembers);
        return rosterMembers.stream()
                .map(member -> new StudentRosterEntry(userIndex.get(member.getUserId()), member.getTeachingClassId()))
                .filter(entry -> entry.user() != null)
                .sorted(Comparator.comparing((StudentRosterEntry entry) ->
                                teachingClassSortKey(teachingClassIndex.get(entry.teachingClassId())))
                        .thenComparing(entry -> entry.user().getUsername())
                        .thenComparing(entry -> entry.user().getId()))
                .toList();
    }

    private static boolean isHistoricalRosterMember(CourseMemberEntity member) {
        return !CourseMemberStatus.ACTIVE.name().equals(member.getMemberStatus());
    }

    private static boolean hasMissingTeachingClass(CourseMemberEntity member) {
        return member.getTeachingClassId() == null;
    }

    private static OffsetDateTime rosterMembershipRecency(CourseMemberEntity member) {
        if (member.getLeftAt() != null) {
            return member.getLeftAt();
        }
        if (member.getJoinedAt() != null) {
            return member.getJoinedAt();
        }
        return member.getCreatedAt();
    }

    private Map<Long, TeachingClassEntity> loadTeachingClassIndex(
            Collection<AssignmentEntity> assignments, Collection<StudentRosterEntry> roster) {
        LinkedHashSet<Long> classIds = new LinkedHashSet<>();
        assignments.stream()
                .map(AssignmentEntity::getTeachingClassId)
                .filter(Objects::nonNull)
                .forEach(classIds::add);
        roster.stream()
                .map(StudentRosterEntry::teachingClassId)
                .filter(Objects::nonNull)
                .forEach(classIds::add);
        return loadTeachingClassesByIds(classIds);
    }

    private Map<Long, TeachingClassEntity> loadTeachingClassIndex(Collection<CourseMemberEntity> members) {
        LinkedHashSet<Long> classIds = new LinkedHashSet<>();
        members.stream()
                .map(CourseMemberEntity::getTeachingClassId)
                .filter(Objects::nonNull)
                .forEach(classIds::add);
        return loadTeachingClassesByIds(classIds);
    }

    private Map<Long, TeachingClassEntity> loadTeachingClassesByIds(Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectByIds(classIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TeachingClassEntity::getId,
                        teachingClass -> teachingClass,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<Long, SubmissionEntity> loadLatestSubmissions(
            List<AssignmentEntity> assignments, List<Long> studentUserIds) {
        if (assignments.isEmpty() || studentUserIds.isEmpty()) {
            return Map.of();
        }
        List<SubmissionEntity> submissions = submissionMapper.selectList(Wrappers.<SubmissionEntity>lambdaQuery()
                .in(
                        SubmissionEntity::getAssignmentId,
                        assignments.stream().map(AssignmentEntity::getId).toList())
                .in(SubmissionEntity::getSubmitterUserId, studentUserIds)
                .orderByDesc(SubmissionEntity::getSubmittedAt)
                .orderByDesc(SubmissionEntity::getId));
        Map<Long, SubmissionEntity> latest = new LinkedHashMap<>();
        for (SubmissionEntity submission : submissions) {
            long key = latestSubmissionKey(submission.getAssignmentId(), submission.getSubmitterUserId());
            latest.putIfAbsent(key, submission);
        }
        return latest;
    }

    private Map<Long, SubmissionScoreSummaryView> loadScoreSummaries(
            Collection<SubmissionEntity> submissions,
            List<AssignmentEntity> assignments,
            boolean revealNonObjectiveScores) {
        if (submissions.isEmpty()) {
            return Map.of();
        }
        Map<Long, AssignmentEntity> assignmentIndex = assignments.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AssignmentEntity::getId, assignment -> assignment, (left, right) -> left, LinkedHashMap::new));
        Map<Long, SubmissionScoreSummaryView> summaries = new LinkedHashMap<>();
        for (SubmissionEntity submission : submissions) {
            AssignmentEntity assignment = assignmentIndex.get(submission.getAssignmentId());
            if (assignment == null) {
                continue;
            }
            boolean assignmentGradePublished = assignment.getGradePublishedAt() != null;
            summaries.put(
                    submission.getId(),
                    submissionAnswerApplicationService.loadScoreSummary(
                            submission.getId(),
                            assignment.getId(),
                            revealNonObjectiveScores || assignmentGradePublished,
                            assignmentGradePublished));
        }
        return summaries;
    }

    private CourseOfferingEntity requireOffering(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "开课实例不存在");
        }
        return offering;
    }

    private TeachingClassEntity requireTeachingClass(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        return teachingClass;
    }

    private TeachingClassEntity resolveTeachingClassScope(Long offeringId, Long teachingClassId) {
        if (teachingClassId == null) {
            return null;
        }
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        if (!Objects.equals(teachingClass.getOfferingId(), offeringId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TEACHING_CLASS_SCOPE_INVALID", "教学班不属于当前开课实例");
        }
        return teachingClass;
    }

    private boolean isApplicable(Long assignmentTeachingClassId, Long studentTeachingClassId) {
        return assignmentTeachingClassId == null || Objects.equals(assignmentTeachingClassId, studentTeachingClassId);
    }

    private int defaultScore(Integer score) {
        return score == null ? 0 : score;
    }

    private int defaultGradeWeight(Integer gradeWeight) {
        return gradeWeight == null ? 100 : gradeWeight;
    }

    private double average(int value, int count) {
        if (count <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double average(double value, int count) {
        if (count <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double ratio(double numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double weightedScore(Integer finalScore, Integer maxScore, Integer gradeWeight) {
        if (finalScore == null || maxScore == null || maxScore <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(finalScore)
                .multiply(BigDecimal.valueOf(defaultGradeWeight(gradeWeight)))
                .divide(BigDecimal.valueOf(maxScore), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double roundDecimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private double overallScoreRate(GradebookPageView.StudentRowView row) {
        if (defaultScore(row.totalWeight()) > 0) {
            return normalizeRate(row.weightedScoreRate());
        }
        return scoreRate(row.totalFinalScore(), row.totalMaxScore());
    }

    private boolean isPassing(double scoreRate) {
        return normalizeRate(scoreRate) >= PASS_SCORE_RATE;
    }

    private double scoreRate(Integer score, Integer maxScore) {
        if (score == null || maxScore == null || maxScore <= 0) {
            return 0.0;
        }
        return normalizeRate(BigDecimal.valueOf(score)
                .divide(BigDecimal.valueOf(maxScore), 4, RoundingMode.HALF_UP)
                .doubleValue());
    }

    private double normalizeRate(double rate) {
        return Math.max(0.0, Math.min(rate, 1.0));
    }

    private List<GradebookReportView.ScoreBandView> buildScoreBands(List<Double> rates, int population) {
        List<GradebookReportView.ScoreBandView> scoreBands = new ArrayList<>();
        for (ScoreBandDefinition definition : SCORE_BAND_DEFINITIONS) {
            int count = (int) rates.stream()
                    .mapToDouble(Double::doubleValue)
                    .filter(rate -> definition.matches(normalizeRate(rate)))
                    .count();
            scoreBands.add(new GradebookReportView.ScoreBandView(
                    definition.bandCode(),
                    definition.label(),
                    definition.minPercentInclusive(),
                    definition.maxPercentExclusive(),
                    count,
                    ratio(count, population)));
        }
        return List.copyOf(scoreBands);
    }

    private String teachingClassSortKey(TeachingClassEntity teachingClass) {
        if (teachingClass == null) {
            return "zzz";
        }
        String className = teachingClass.getClassName();
        return className == null ? "zzz" : className;
    }

    private long latestSubmissionKey(Long assignmentId, Long userId) {
        return assignmentId * 1_000_000_000L + userId;
    }

    private List<GradebookPageView.StudentRowView> paginate(
            List<GradebookPageView.StudentRowView> rows, long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        long normalizedPageSize = Math.max(pageSize, 1);
        int fromIndex = (int) Math.min(rows.size(), (normalizedPage - 1) * normalizedPageSize);
        int toIndex = (int) Math.min(rows.size(), fromIndex + normalizedPageSize);
        return List.copyOf(rows.subList(fromIndex, toIndex));
    }

    private record StudentRosterEntry(UserEntity user, Long teachingClassId) {}

    private record GradebookSnapshot(
            GradebookPageView.ScopeView scope,
            GradebookPageView.SummaryView summary,
            List<GradebookPageView.AssignmentColumnView> assignmentColumns,
            List<GradebookPageView.StudentRowView> rows) {}

    private static final class MutableTeachingClassStat {
        private final Long teachingClassId;
        private final String teachingClassCode;
        private final String teachingClassName;
        private int studentCount;
        private int applicableAssignmentCount;
        private int submittedAssignmentCount;
        private int gradedAssignmentCount;
        private int publishedAssignmentCount;
        private int passedStudentCount;
        private int totalFinalScore;
        private int totalMaxScore;
        private int totalWeight;
        private BigDecimal totalWeightedScore = BigDecimal.ZERO;
        private final List<Double> scoreRates = new ArrayList<>();

        private MutableTeachingClassStat(Long teachingClassId, String teachingClassCode, String teachingClassName) {
            this.teachingClassId = teachingClassId;
            this.teachingClassCode = teachingClassCode;
            this.teachingClassName = teachingClassName;
        }
    }

    private record ScoreBandDefinition(
            String bandCode,
            String label,
            int minPercentInclusive,
            Integer maxPercentExclusive,
            double minRateInclusive,
            double maxRateExclusive) {
        private boolean matches(double rate) {
            return rate >= minRateInclusive && rate < maxRateExclusive;
        }
    }

    private record StudentRank(Integer offeringRank, Integer teachingClassRank) {
        private static final StudentRank EMPTY = new StudentRank(null, null);
    }
}
