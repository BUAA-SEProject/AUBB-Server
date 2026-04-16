package com.aubb.server.modules.grading.application.gradebook;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.assignment.domain.AssignmentStatus;
import com.aubb.server.modules.assignment.infrastructure.AssignmentEntity;
import com.aubb.server.modules.assignment.infrastructure.AssignmentMapper;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionEntity;
import com.aubb.server.modules.assignment.infrastructure.paper.AssignmentSectionMapper;
import com.aubb.server.modules.course.application.CourseAuthorizationService;
import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.user.UserMapper;
import com.aubb.server.modules.submission.application.answer.SubmissionAnswerApplicationService;
import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import com.aubb.server.modules.submission.infrastructure.SubmissionEntity;
import com.aubb.server.modules.submission.infrastructure.SubmissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    private final CourseOfferingMapper courseOfferingMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMemberMapper courseMemberMapper;
    private final UserMapper userMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentSectionMapper assignmentSectionMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerApplicationService submissionAnswerApplicationService;
    private final CourseAuthorizationService courseAuthorizationService;

    @Transactional(readOnly = true)
    public GradebookPageView getOfferingGradebook(
            Long offeringId,
            Long teachingClassId,
            Long studentUserId,
            long page,
            long pageSize,
            AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);
        TeachingClassEntity teachingClass = resolveTeachingClassScope(offeringId, teachingClassId);
        return buildGradebook(offering, teachingClass, studentUserId, page, pageSize);
    }

    @Transactional(readOnly = true)
    public GradebookPageView getTeachingClassGradebook(
            Long teachingClassId, Long studentUserId, long page, long pageSize, AuthenticatedUserPrincipal principal) {
        TeachingClassEntity teachingClass = requireTeachingClass(teachingClassId);
        CourseOfferingEntity offering = requireOffering(teachingClass.getOfferingId());
        courseAuthorizationService.assertCanGradeSubmission(principal, offering.getId(), teachingClassId);
        return buildGradebook(offering, teachingClass, studentUserId, page, pageSize);
    }

    @Transactional(readOnly = true)
    public StudentGradebookView getStudentGradebook(
            Long offeringId, Long studentUserId, AuthenticatedUserPrincipal principal) {
        CourseOfferingEntity offering = requireOffering(offeringId);
        courseAuthorizationService.assertCanManageAssignments(principal, offeringId);

        List<StudentRosterEntry> roster = loadRoster(offeringId, null, studentUserId);
        StudentRosterEntry student = roster.stream()
                .filter(candidate -> Objects.equals(candidate.user().getId(), studentUserId))
                .findFirst()
                .orElseThrow(
                        () -> new BusinessException(HttpStatus.NOT_FOUND, "COURSE_STUDENT_NOT_FOUND", "当前学生不在课程名册中"));

        List<AssignmentEntity> assignments = loadStructuredAssignments(offeringId, null);
        Map<Long, Integer> assignmentMaxScores = loadAssignmentMaxScores(assignments);
        Map<Long, SubmissionEntity> latestSubmissions =
                loadLatestSubmissions(assignments, List.of(student.user().getId()));
        Map<Long, SubmissionScoreSummaryView> scoreSummaries =
                loadScoreSummaries(latestSubmissions.values(), assignments);
        Map<Long, TeachingClassEntity> classIndex = loadTeachingClassIndex(assignments, roster);

        List<StudentGradebookView.AssignmentGradeView> assignmentViews = new ArrayList<>();
        int submittedCount = 0;
        int gradedCount = 0;
        int totalFinalScore = 0;
        int totalMaxScore = 0;
        for (AssignmentEntity assignment : assignments) {
            if (!isApplicable(assignment.getTeachingClassId(), student.teachingClassId())) {
                continue;
            }
            int assignmentMaxScore = assignmentMaxScores.getOrDefault(assignment.getId(), 0);
            totalMaxScore += assignmentMaxScore;
            SubmissionEntity submission = latestSubmissions.get(
                    latestSubmissionKey(assignment.getId(), student.user().getId()));
            SubmissionScoreSummaryView summary = submission == null ? null : scoreSummaries.get(submission.getId());
            GradebookPageView.GradeCellView gradeCell =
                    toGradeCell(assignment, assignmentMaxScore, true, submission, summary);
            if (Boolean.TRUE.equals(gradeCell.submitted())) {
                submittedCount++;
            }
            if (Boolean.TRUE.equals(gradeCell.fullyGraded())) {
                gradedCount++;
            }
            if (gradeCell.finalScore() != null) {
                totalFinalScore += gradeCell.finalScore();
            }
            assignmentViews.add(new StudentGradebookView.AssignmentGradeView(
                    toAssignmentColumn(assignment, assignmentMaxScore, classIndex.get(assignment.getTeachingClassId())),
                    gradeCell));
        }

        TeachingClassEntity studentClass = classIndex.get(student.teachingClassId());
        return new StudentGradebookView(
                toScope(offering.getId(), studentClass),
                new StudentGradebookView.StudentView(
                        student.user().getId(),
                        student.user().getUsername(),
                        student.user().getDisplayName(),
                        student.teachingClassId(),
                        studentClass == null ? null : studentClass.getClassCode(),
                        studentClass == null ? null : studentClass.getClassName()),
                new StudentGradebookView.SummaryView(
                        assignmentViews.size(), submittedCount, gradedCount, totalFinalScore, totalMaxScore),
                List.copyOf(assignmentViews));
    }

    private GradebookPageView buildGradebook(
            CourseOfferingEntity offering,
            TeachingClassEntity teachingClass,
            Long studentUserId,
            long page,
            long pageSize) {
        Long teachingClassId = teachingClass == null ? null : teachingClass.getId();
        List<AssignmentEntity> assignments = loadStructuredAssignments(offering.getId(), teachingClassId);
        List<StudentRosterEntry> roster = loadRoster(offering.getId(), teachingClassId, studentUserId);
        Map<Long, Integer> assignmentMaxScores = loadAssignmentMaxScores(assignments);
        List<Long> studentUserIds =
                roster.stream().map(entry -> entry.user().getId()).toList();
        Map<Long, SubmissionEntity> latestSubmissions = loadLatestSubmissions(assignments, studentUserIds);
        Map<Long, SubmissionScoreSummaryView> scoreSummaries =
                loadScoreSummaries(latestSubmissions.values(), assignments);
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

        List<GradebookPageView.StudentRowView> pagedRows = paginate(rows, page, pageSize);
        return new GradebookPageView(
                toScope(offering.getId(), teachingClass),
                new GradebookPageView.SummaryView(
                        assignments.size(), roster.size(), submittedCount, fullyGradedCount, publishedCount),
                assignmentColumns,
                pagedRows,
                rows.size(),
                page,
                pageSize);
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
        int submittedAssignmentCount = 0;
        int gradedAssignmentCount = 0;
        for (AssignmentEntity assignment : assignments) {
            int assignmentMaxScore = assignmentMaxScores.getOrDefault(assignment.getId(), 0);
            boolean applicable = isApplicable(assignment.getTeachingClassId(), student.teachingClassId());
            if (applicable) {
                totalMaxScore += assignmentMaxScore;
            }
            SubmissionEntity submission = latestSubmissions.get(
                    latestSubmissionKey(assignment.getId(), student.user().getId()));
            SubmissionScoreSummaryView summary = submission == null ? null : scoreSummaries.get(submission.getId());
            GradebookPageView.GradeCellView cell =
                    toGradeCell(assignment, assignmentMaxScore, applicable, submission, summary);
            if (applicable && Boolean.TRUE.equals(cell.submitted())) {
                submittedAssignmentCount++;
            }
            if (applicable && Boolean.TRUE.equals(cell.fullyGraded())) {
                gradedAssignmentCount++;
            }
            if (applicable && cell.finalScore() != null) {
                totalFinalScore += cell.finalScore();
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
                submittedAssignmentCount,
                gradedAssignmentCount,
                List.copyOf(grades));
    }

    private GradebookPageView.GradeCellView toGradeCell(
            AssignmentEntity assignment,
            int assignmentMaxScore,
            boolean applicable,
            SubmissionEntity submission,
            SubmissionScoreSummaryView summary) {
        if (!applicable) {
            return new GradebookPageView.GradeCellView(
                    assignment.getId(), false, false, null, null, null, null, null, null, null, null);
        }
        boolean submitted = submission != null;
        return new GradebookPageView.GradeCellView(
                assignment.getId(),
                true,
                submitted,
                submitted ? submission.getId() : null,
                submitted ? submission.getAttemptNo() : null,
                submitted ? submission.getSubmittedAt() : null,
                summary,
                summary == null ? null : summary.finalScore(),
                assignmentMaxScore,
                summary == null ? null : summary.fullyGraded(),
                summary == null ? assignment.getGradePublishedAt() != null : summary.gradePublished());
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

    private List<StudentRosterEntry> loadRoster(Long offeringId, Long teachingClassId, Long studentUserId) {
        List<CourseMemberEntity> members = courseMemberMapper.selectList(Wrappers.<CourseMemberEntity>lambdaQuery()
                .eq(CourseMemberEntity::getOfferingId, offeringId)
                .eq(CourseMemberEntity::getMemberRole, CourseMemberRole.STUDENT.name())
                .eq(CourseMemberEntity::getMemberStatus, CourseMemberStatus.ACTIVE.name())
                .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId));
        if (members.isEmpty()) {
            return List.of();
        }
        members = members.stream()
                .sorted(Comparator.comparing((CourseMemberEntity member) -> member.getTeachingClassId() == null ? 1 : 0)
                        .thenComparing(member -> member.getTeachingClassId() == null ? 0L : member.getTeachingClassId())
                        .thenComparing(CourseMemberEntity::getId))
                .toList();
        Map<Long, CourseMemberEntity> rosterByUserId = new LinkedHashMap<>();
        for (CourseMemberEntity member : members) {
            if (studentUserId != null && !Objects.equals(member.getUserId(), studentUserId)) {
                continue;
            }
            CourseMemberEntity existing = rosterByUserId.get(member.getUserId());
            if (existing == null || (existing.getTeachingClassId() == null && member.getTeachingClassId() != null)) {
                rosterByUserId.put(member.getUserId(), member);
            }
        }
        if (rosterByUserId.isEmpty()) {
            return List.of();
        }
        Map<Long, UserEntity> userIndex = userMapper.selectByIds(rosterByUserId.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserEntity::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
        Map<Long, TeachingClassEntity> teachingClassIndex = loadTeachingClassIndex(rosterByUserId.values());
        return rosterByUserId.values().stream()
                .map(member -> new StudentRosterEntry(userIndex.get(member.getUserId()), member.getTeachingClassId()))
                .filter(entry -> entry.user() != null)
                .sorted(Comparator.comparing((StudentRosterEntry entry) ->
                                teachingClassSortKey(teachingClassIndex.get(entry.teachingClassId())))
                        .thenComparing(entry -> entry.user().getUsername())
                        .thenComparing(entry -> entry.user().getId()))
                .toList();
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
            Collection<SubmissionEntity> submissions, List<AssignmentEntity> assignments) {
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
            summaries.put(
                    submission.getId(),
                    submissionAnswerApplicationService.loadScoreSummary(
                            submission.getId(), assignment.getId(), true, assignment.getGradePublishedAt() != null));
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
}
