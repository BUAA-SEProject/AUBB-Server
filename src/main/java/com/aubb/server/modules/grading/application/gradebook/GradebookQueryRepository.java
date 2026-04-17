package com.aubb.server.modules.grading.application.gradebook;

import com.aubb.server.modules.submission.application.answer.SubmissionScoreSummaryView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GradebookQueryRepository {

    private static final List<ScoreBandDefinition> SCORE_BANDS = List.of(
            new ScoreBandDefinition("EXCELLENT", "优秀", 90, null),
            new ScoreBandDefinition("GOOD", "良好", 80, 90),
            new ScoreBandDefinition("MEDIUM", "中等", 70, 80),
            new ScoreBandDefinition("PASS", "及格", 60, 70),
            new ScoreBandDefinition("FAIL", "不及格", 0, 60));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    GradebookPageAggregate loadOfferingPage(
            Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId, long page, long pageSize) {
        GradebookPageView.ScopeView scope = loadScope(offeringId, teachingClassId);
        List<GradebookPageView.AssignmentColumnView> assignmentColumns =
                loadAssignmentColumns(offeringId, teachingClassId);
        if (assignmentColumns.isEmpty()) {
            return loadRosterOnlyPage(scope, offeringId, teachingClassId, studentUserId, page, pageSize);
        }
        GradebookPageView.SummaryView summary =
                loadSummaryWithAssignments(offeringId, teachingClassId, studentUserId, assignmentColumns.size());
        List<GradebookPageView.StudentRowView> items =
                loadAssignmentBackedRows(offeringId, teachingClassId, studentUserId, page, pageSize, assignmentColumns);
        return new GradebookPageAggregate(scope, summary, assignmentColumns, items, summary.studentCount());
    }

    GradebookReportAggregate loadOfferingReport(
            Long offeringId,
            @Nullable Long teachingClassId,
            @Nullable Long studentUserId,
            boolean includeTeachingClasses) {
        GradebookPageView.ScopeView scope = loadScope(offeringId, teachingClassId);
        List<GradebookPageView.AssignmentColumnView> assignmentColumns =
                loadAssignmentColumns(offeringId, teachingClassId);
        if (assignmentColumns.isEmpty()) {
            return loadRosterOnlyReport(scope, offeringId, teachingClassId, studentUserId, includeTeachingClasses);
        }
        GradebookReportView.OverviewView overview =
                loadOverview(offeringId, teachingClassId, studentUserId, assignmentColumns.size());
        return new GradebookReportAggregate(
                scope,
                overview,
                loadAssignmentStats(offeringId, teachingClassId, studentUserId),
                includeTeachingClasses
                        ? loadTeachingClassStats(offeringId, teachingClassId, studentUserId)
                        : List.of());
    }

    private GradebookPageAggregate loadRosterOnlyPage(
            GradebookPageView.ScopeView scope,
            Long offeringId,
            Long teachingClassId,
            Long studentUserId,
            long page,
            long pageSize) {
        long total = loadRosterCount(offeringId, teachingClassId, studentUserId);
        List<GradebookPageView.StudentRowView> items = jdbcTemplate.query(
                GradebookQuerySql.rosterCtes() + """
                                SELECT
                                    roster.user_id,
                                    roster.username,
                                    roster.display_name,
                                    roster.teaching_class_id,
                                    roster.class_code,
                                    roster.class_name,
                                    1 AS offering_rank,
                                    1 AS teaching_class_rank
                                FROM roster
                                ORDER BY COALESCE(roster.class_name, 'zzz'), roster.username, roster.user_id
                                LIMIT :limit OFFSET :offset
                                """,
                params(offeringId, teachingClassId, studentUserId)
                        .addValue("limit", normalize(pageSize))
                        .addValue("offset", offset(page, pageSize)),
                (rs, ignored) -> new GradebookPageView.StudentRowView(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        getNullableLong(rs, "teaching_class_id"),
                        rs.getString("class_code"),
                        rs.getString("class_name"),
                        0,
                        0,
                        0.0,
                        0,
                        0.0,
                        rs.getInt("offering_rank"),
                        rs.getInt("teaching_class_rank"),
                        0,
                        0,
                        List.of()));
        return new GradebookPageAggregate(
                scope, new GradebookPageView.SummaryView(0, (int) total, 0, 0, 0), List.of(), items, total);
    }

    private GradebookReportAggregate loadRosterOnlyReport(
            GradebookPageView.ScopeView scope,
            Long offeringId,
            Long teachingClassId,
            Long studentUserId,
            boolean includeTeachingClasses) {
        int studentCount = Math.toIntExact(loadRosterCount(offeringId, teachingClassId, studentUserId));
        List<GradebookReportView.TeachingClassStatView> teachingClasses = includeTeachingClasses
                ? jdbcTemplate.query(
                        GradebookQuerySql.rosterCtes() + """
                                        SELECT
                                            roster.teaching_class_id,
                                            roster.class_code,
                                            roster.class_name,
                                            COUNT(*) AS student_count
                                        FROM roster
                                        GROUP BY roster.teaching_class_id, roster.class_code, roster.class_name
                                        ORDER BY COALESCE(roster.class_name, 'zzz'), roster.teaching_class_id
                                        """,
                        params(offeringId, teachingClassId, studentUserId),
                        (rs, ignored) -> new GradebookReportView.TeachingClassStatView(
                                getNullableLong(rs, "teaching_class_id"),
                                rs.getString("class_code"),
                                rs.getString("class_name"),
                                rs.getInt("student_count"),
                                0,
                                0,
                                0,
                                0,
                                0,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                zeroScoreBands(rs.getInt("student_count"))))
                : List.of();
        return new GradebookReportAggregate(
                scope,
                new GradebookReportView.OverviewView(
                        0,
                        studentCount,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        zeroScoreBands(studentCount)),
                List.of(),
                teachingClasses);
    }

    private GradebookPageView.ScopeView loadScope(Long offeringId, @Nullable Long teachingClassId) {
        if (teachingClassId == null) {
            return new GradebookPageView.ScopeView(offeringId, null, null, null);
        }
        return jdbcTemplate.queryForObject(
                """
                        SELECT class_code, class_name
                        FROM teaching_classes
                        WHERE id = :teachingClassId
                        """,
                new MapSqlParameterSource("teachingClassId", teachingClassId),
                (rs, ignored) -> new GradebookPageView.ScopeView(
                        offeringId, teachingClassId, rs.getString("class_code"), rs.getString("class_name")));
    }

    private List<GradebookPageView.AssignmentColumnView> loadAssignmentColumns(
            Long offeringId, @Nullable Long teachingClassId) {
        return jdbcTemplate.query(
                GradebookQuerySql.assignmentBackedCtes() + """
                                SELECT
                                    assignment_defs.assignment_id,
                                    assignment_defs.teaching_class_id,
                                    assignment_defs.teaching_class_name,
                                    assignment_defs.title,
                                    assignment_defs.status,
                                    assignment_defs.open_at,
                                    assignment_defs.due_at,
                                    assignment_defs.max_score,
                                    assignment_defs.grade_weight,
                                    assignment_defs.grade_published
                                FROM assignment_defs
                                ORDER BY assignment_defs.open_at, assignment_defs.assignment_id
                                """,
                params(offeringId, teachingClassId, null),
                (rs, ignored) -> new GradebookPageView.AssignmentColumnView(
                        rs.getLong("assignment_id"),
                        getNullableLong(rs, "teaching_class_id"),
                        rs.getString("teaching_class_name"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getObject("open_at", OffsetDateTime.class),
                        rs.getObject("due_at", OffsetDateTime.class),
                        rs.getInt("max_score"),
                        rs.getInt("grade_weight"),
                        rs.getBoolean("grade_published")));
    }

    private GradebookPageView.SummaryView loadSummaryWithAssignments(
            Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId, int assignmentCount) {
        return jdbcTemplate.queryForObject(
                GradebookQuerySql.assignmentBackedCtes() + """
                                SELECT
                                    COUNT(*) AS student_count,
                                    COALESCE(SUM(ranked_rows.submitted_assignment_count), 0) AS submitted_count,
                                    COALESCE(SUM(ranked_rows.graded_assignment_count), 0) AS graded_count,
                                    COALESCE(SUM(ranked_rows.published_assignment_count), 0) AS published_count
                                FROM ranked_rows
                                """,
                params(offeringId, teachingClassId, studentUserId),
                (rs, ignored) -> new GradebookPageView.SummaryView(
                        assignmentCount,
                        rs.getInt("student_count"),
                        rs.getInt("submitted_count"),
                        rs.getInt("graded_count"),
                        rs.getInt("published_count")));
    }

    private List<GradebookPageView.StudentRowView> loadAssignmentBackedRows(
            Long offeringId,
            @Nullable Long teachingClassId,
            @Nullable Long studentUserId,
            long page,
            long pageSize,
            List<GradebookPageView.AssignmentColumnView> assignmentColumns) {
        Map<Long, GradebookPageView.StudentRowView> rows = new LinkedHashMap<>();
        MapSqlParameterSource params = params(offeringId, teachingClassId, studentUserId)
                .addValue("limit", normalize(pageSize))
                .addValue("offset", offset(page, pageSize));
        jdbcTemplate.query(
                GradebookQuerySql.assignmentBackedCtes() + """
                                , page_rows AS (
                                    SELECT *
                                    FROM ranked_rows
                                    ORDER BY COALESCE(class_name, 'zzz'), username, user_id
                                    LIMIT :limit OFFSET :offset
                                )
                                SELECT
                                    page_rows.*
                                FROM page_rows
                                ORDER BY COALESCE(page_rows.class_name, 'zzz'), page_rows.username, page_rows.user_id
                """,
                params,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> rows.put(
                        rs.getLong("user_id"),
                        new GradebookPageView.StudentRowView(
                                rs.getLong("user_id"),
                                rs.getString("username"),
                                rs.getString("display_name"),
                                getNullableLong(rs, "teaching_class_id"),
                                rs.getString("class_code"),
                                rs.getString("class_name"),
                                rs.getInt("total_final_score"),
                                rs.getInt("total_max_score"),
                                rs.getBigDecimal("total_weighted_score").doubleValue(),
                                rs.getInt("total_weight"),
                                ratio(rs.getBigDecimal("total_weighted_score"), rs.getInt("total_weight")),
                                rs.getInt("offering_rank"),
                                rs.getInt("teaching_class_rank"),
                                rs.getInt("submitted_assignment_count"),
                                rs.getInt("graded_assignment_count"),
                                new ArrayList<>())));
        if (rows.isEmpty()) {
            return List.of();
        }
        jdbcTemplate.query(
                GradebookQuerySql.assignmentBackedCtes() + """
                                , page_rows AS (
                                    SELECT *
                                    FROM ranked_rows
                                    ORDER BY COALESCE(class_name, 'zzz'), username, user_id
                                    LIMIT :limit OFFSET :offset
                                )
                                SELECT
                                    page_rows.user_id,
                                    assignment_defs.assignment_id,
                                    (assignment_defs.teaching_class_id IS NULL
                                        OR assignment_defs.teaching_class_id = page_rows.teaching_class_id) AS applicable,
                                    latest_submissions.submission_id,
                                    latest_submissions.attempt_no,
                                    latest_submissions.submitted_at,
                                    submission_scores.auto_score,
                                    submission_scores.manual_score,
                                    submission_scores.final_score,
                                    submission_scores.pending_manual_count,
                                    submission_scores.pending_programming_count,
                                    assignment_defs.max_score,
                                    assignment_defs.grade_weight,
                                    assignment_defs.grade_published,
                                    CASE
                                        WHEN (assignment_defs.teaching_class_id IS NULL
                                                OR assignment_defs.teaching_class_id = page_rows.teaching_class_id)
                                                AND latest_submissions.submission_id IS NOT NULL
                                                AND assignment_defs.max_score > 0
                                            THEN ROUND(
                                                submission_scores.final_score::numeric
                                                    * assignment_defs.grade_weight::numeric
                                                    / assignment_defs.max_score::numeric,
                                                2
                                            )
                                        ELSE NULL
                                    END AS weighted_score,
                                    CASE
                                        WHEN (assignment_defs.teaching_class_id IS NULL
                                                OR assignment_defs.teaching_class_id = page_rows.teaching_class_id)
                                                AND latest_submissions.submission_id IS NOT NULL
                                            THEN (
                                                submission_scores.pending_manual_count = 0
                                                AND submission_scores.pending_programming_count = 0
                                            )
                                        ELSE NULL
                                    END AS fully_graded
                                FROM page_rows
                                CROSS JOIN assignment_defs
                                LEFT JOIN latest_submissions
                                    ON latest_submissions.assignment_id = assignment_defs.assignment_id
                                   AND latest_submissions.user_id = page_rows.user_id
                                LEFT JOIN submission_scores
                                    ON submission_scores.submission_id = latest_submissions.submission_id
                                ORDER BY
                                    COALESCE(page_rows.class_name, 'zzz'),
                                    page_rows.username,
                                    page_rows.user_id,
                                    assignment_defs.open_at,
                                    assignment_defs.assignment_id
                                """,
                params,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> ((List<GradebookPageView.GradeCellView>)
                                rows.get(rs.getLong("user_id")).grades())
                        .add(toGradeCell(rs)));
        return rows.values().stream()
                .map(row -> new GradebookPageView.StudentRowView(
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
                        row.offeringRank(),
                        row.teachingClassRank(),
                        row.submittedAssignmentCount(),
                        row.gradedAssignmentCount(),
                        List.copyOf(row.grades())))
                .toList();
    }

    private GradebookReportView.OverviewView loadOverview(
            Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId, int assignmentCount) {
        return jdbcTemplate.queryForObject(
                GradebookQuerySql.assignmentBackedCtes() + """
                                SELECT
                                    COUNT(*) AS student_count,
                                    COALESCE(SUM(ranked_rows.applicable_assignment_count), 0) AS applicable_grade_count,
                                    COALESCE(SUM(ranked_rows.submitted_assignment_count), 0) AS submitted_count,
                                    COALESCE(SUM(ranked_rows.graded_assignment_count), 0) AS graded_count,
                                    COALESCE(SUM(ranked_rows.published_assignment_count), 0) AS published_count,
                                    COUNT(*) FILTER (WHERE ranked_rows.overall_score_rate >= 0.6) AS passed_student_count,
                                    COALESCE(SUM(ranked_rows.total_final_score), 0) AS total_final_score,
                                    COALESCE(SUM(ranked_rows.total_max_score), 0) AS total_max_score,
                                    COALESCE(SUM(ranked_rows.total_weighted_score), 0) AS total_weighted_score,
                                    COALESCE(SUM(ranked_rows.total_weight), 0) AS total_weight,
                                    COUNT(*) FILTER (WHERE ranked_rows.overall_score_rate >= 0.9) AS excellent_count,
                                    COUNT(*) FILTER (
                                        WHERE ranked_rows.overall_score_rate >= 0.8
                                          AND ranked_rows.overall_score_rate < 0.9
                                    ) AS good_count,
                                    COUNT(*) FILTER (
                                        WHERE ranked_rows.overall_score_rate >= 0.7
                                          AND ranked_rows.overall_score_rate < 0.8
                                    ) AS medium_count,
                                    COUNT(*) FILTER (
                                        WHERE ranked_rows.overall_score_rate >= 0.6
                                          AND ranked_rows.overall_score_rate < 0.7
                                    ) AS pass_count,
                                    COUNT(*) FILTER (WHERE ranked_rows.overall_score_rate < 0.6) AS fail_count
                                FROM ranked_rows
                                """,
                params(offeringId, teachingClassId, studentUserId),
                (rs, ignored) -> {
                    int studentCount = rs.getInt("student_count");
                    int applicableGradeCount = rs.getInt("applicable_grade_count");
                    int submittedCount = rs.getInt("submitted_count");
                    int gradedCount = rs.getInt("graded_count");
                    int publishedCount = rs.getInt("published_count");
                    return new GradebookReportView.OverviewView(
                            assignmentCount,
                            studentCount,
                            applicableGradeCount,
                            submittedCount,
                            gradedCount,
                            publishedCount,
                            rs.getInt("passed_student_count"),
                            ratio(submittedCount, applicableGradeCount),
                            ratio(gradedCount, applicableGradeCount),
                            ratio(publishedCount, applicableGradeCount),
                            ratio(rs.getInt("passed_student_count"), studentCount),
                            average(rs.getBigDecimal("total_final_score"), studentCount, 2),
                            ratio(rs.getBigDecimal("total_final_score"), rs.getInt("total_max_score")),
                            average(rs.getBigDecimal("total_weighted_score"), studentCount, 2),
                            ratio(rs.getBigDecimal("total_weighted_score"), rs.getInt("total_weight")),
                            toScoreBands(
                                    rs.getInt("excellent_count"),
                                    rs.getInt("good_count"),
                                    rs.getInt("medium_count"),
                                    rs.getInt("pass_count"),
                                    rs.getInt("fail_count"),
                                    studentCount));
                });
    }

    private List<GradebookReportView.AssignmentStatView> loadAssignmentStats(
            Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId) {
        return jdbcTemplate.query(
                GradebookQuerySql.assignmentBackedCtes() + """
                                , assignment_scores AS (
                                    SELECT
                                        grade_matrix.*,
                                        CASE
                                            WHEN grade_matrix.submission_id IS NOT NULL AND grade_matrix.max_score > 0
                                                THEN LEAST(GREATEST(
                                                    ROUND(grade_matrix.final_score::numeric / grade_matrix.max_score::numeric, 4),
                                                    0
                                                ), 1)
                                            ELSE NULL
                                        END AS score_rate
                                    FROM grade_matrix
                                )
                                SELECT
                                    assignment_defs.assignment_id,
                                    assignment_defs.teaching_class_id,
                                    assignment_defs.teaching_class_name,
                                    assignment_defs.title,
                                    assignment_defs.max_score,
                                    assignment_defs.grade_weight,
                                    COUNT(*) FILTER (WHERE assignment_scores.applicable) AS applicable_student_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                    ) AS submitted_student_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.fully_graded IS TRUE
                                    ) AS graded_student_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_defs.grade_published
                                    ) AS published_student_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_scores.score_rate >= 0.6
                                    ) AS passed_student_count,
                                    COALESCE(SUM(
                                        CASE
                                            WHEN assignment_scores.applicable
                                              AND assignment_scores.submission_id IS NOT NULL
                                                THEN assignment_scores.final_score
                                            ELSE 0
                                        END
                                    ), 0) AS total_final_score,
                                    COALESCE(SUM(
                                        CASE
                                            WHEN assignment_scores.applicable
                                              AND assignment_scores.submission_id IS NOT NULL
                                                THEN assignment_scores.max_score
                                            ELSE 0
                                        END
                                    ), 0) AS total_max_score,
                                    COALESCE(SUM(
                                        CASE
                                            WHEN assignment_scores.applicable
                                              AND assignment_scores.submission_id IS NOT NULL
                                                THEN assignment_scores.weighted_score
                                            ELSE 0
                                        END
                                    ), 0) AS total_weighted_score,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_scores.score_rate >= 0.9
                                    ) AS excellent_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_scores.score_rate >= 0.8
                                          AND assignment_scores.score_rate < 0.9
                                    ) AS good_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_scores.score_rate >= 0.7
                                          AND assignment_scores.score_rate < 0.8
                                    ) AS medium_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_scores.score_rate >= 0.6
                                          AND assignment_scores.score_rate < 0.7
                                    ) AS pass_count,
                                    COUNT(*) FILTER (
                                        WHERE assignment_scores.applicable
                                          AND assignment_scores.submission_id IS NOT NULL
                                          AND assignment_scores.score_rate < 0.6
                                    ) AS fail_count
                                FROM assignment_scores
                                JOIN assignment_defs ON assignment_defs.assignment_id = assignment_scores.assignment_id
                                GROUP BY
                                    assignment_defs.assignment_id,
                                    assignment_defs.teaching_class_id,
                                    assignment_defs.teaching_class_name,
                                    assignment_defs.title,
                                    assignment_defs.max_score,
                                    assignment_defs.grade_weight,
                                    assignment_defs.open_at
                                ORDER BY assignment_defs.open_at, assignment_defs.assignment_id
                                """,
                params(offeringId, teachingClassId, studentUserId),
                (rs, ignored) -> {
                    int submittedCount = rs.getInt("submitted_student_count");
                    int applicableCount = rs.getInt("applicable_student_count");
                    return new GradebookReportView.AssignmentStatView(
                            rs.getLong("assignment_id"),
                            getNullableLong(rs, "teaching_class_id"),
                            rs.getString("teaching_class_name"),
                            rs.getString("title"),
                            rs.getInt("max_score"),
                            rs.getInt("grade_weight"),
                            applicableCount,
                            submittedCount,
                            rs.getInt("graded_student_count"),
                            rs.getInt("published_student_count"),
                            rs.getInt("passed_student_count"),
                            ratio(submittedCount, applicableCount),
                            ratio(rs.getInt("graded_student_count"), applicableCount),
                            ratio(rs.getInt("published_student_count"), applicableCount),
                            ratio(rs.getInt("passed_student_count"), submittedCount),
                            average(rs.getBigDecimal("total_final_score"), submittedCount, 2),
                            ratio(rs.getBigDecimal("total_final_score"), rs.getInt("total_max_score")),
                            average(rs.getBigDecimal("total_weighted_score"), submittedCount, 2),
                            toScoreBands(
                                    rs.getInt("excellent_count"),
                                    rs.getInt("good_count"),
                                    rs.getInt("medium_count"),
                                    rs.getInt("pass_count"),
                                    rs.getInt("fail_count"),
                                    submittedCount));
                });
    }

    private List<GradebookReportView.TeachingClassStatView> loadTeachingClassStats(
            Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId) {
        return jdbcTemplate.query(
                GradebookQuerySql.assignmentBackedCtes() + """
                                SELECT
                                    ranked_rows.teaching_class_id,
                                    ranked_rows.class_code,
                                    ranked_rows.class_name,
                                    COUNT(*) AS student_count,
                                    COALESCE(SUM(ranked_rows.applicable_assignment_count), 0) AS applicable_assignment_count,
                                    COALESCE(SUM(ranked_rows.submitted_assignment_count), 0) AS submitted_assignment_count,
                                    COALESCE(SUM(ranked_rows.graded_assignment_count), 0) AS graded_assignment_count,
                                    COALESCE(SUM(ranked_rows.published_assignment_count), 0) AS published_assignment_count,
                                    COUNT(*) FILTER (WHERE ranked_rows.overall_score_rate >= 0.6) AS passed_student_count,
                                    COALESCE(SUM(ranked_rows.total_final_score), 0) AS total_final_score,
                                    COALESCE(SUM(ranked_rows.total_max_score), 0) AS total_max_score,
                                    COALESCE(SUM(ranked_rows.total_weighted_score), 0) AS total_weighted_score,
                                    COALESCE(SUM(ranked_rows.total_weight), 0) AS total_weight,
                                    COUNT(*) FILTER (WHERE ranked_rows.overall_score_rate >= 0.9) AS excellent_count,
                                    COUNT(*) FILTER (
                                        WHERE ranked_rows.overall_score_rate >= 0.8
                                          AND ranked_rows.overall_score_rate < 0.9
                                    ) AS good_count,
                                    COUNT(*) FILTER (
                                        WHERE ranked_rows.overall_score_rate >= 0.7
                                          AND ranked_rows.overall_score_rate < 0.8
                                    ) AS medium_count,
                                    COUNT(*) FILTER (
                                        WHERE ranked_rows.overall_score_rate >= 0.6
                                          AND ranked_rows.overall_score_rate < 0.7
                                    ) AS pass_count,
                                    COUNT(*) FILTER (WHERE ranked_rows.overall_score_rate < 0.6) AS fail_count
                                FROM ranked_rows
                                GROUP BY ranked_rows.teaching_class_id, ranked_rows.class_code, ranked_rows.class_name
                                ORDER BY COALESCE(ranked_rows.class_name, 'zzz'), ranked_rows.teaching_class_id
                                """,
                params(offeringId, teachingClassId, studentUserId),
                (rs, ignored) -> {
                    int studentCount = rs.getInt("student_count");
                    int applicableCount = rs.getInt("applicable_assignment_count");
                    return new GradebookReportView.TeachingClassStatView(
                            getNullableLong(rs, "teaching_class_id"),
                            rs.getString("class_code"),
                            rs.getString("class_name"),
                            studentCount,
                            applicableCount,
                            rs.getInt("submitted_assignment_count"),
                            rs.getInt("graded_assignment_count"),
                            rs.getInt("published_assignment_count"),
                            rs.getInt("passed_student_count"),
                            ratio(rs.getInt("submitted_assignment_count"), applicableCount),
                            ratio(rs.getInt("graded_assignment_count"), applicableCount),
                            ratio(rs.getInt("published_assignment_count"), applicableCount),
                            ratio(rs.getInt("passed_student_count"), studentCount),
                            average(rs.getBigDecimal("total_final_score"), studentCount, 2),
                            ratio(rs.getBigDecimal("total_final_score"), rs.getInt("total_max_score")),
                            average(rs.getBigDecimal("total_weighted_score"), studentCount, 2),
                            ratio(rs.getBigDecimal("total_weighted_score"), rs.getInt("total_weight")),
                            toScoreBands(
                                    rs.getInt("excellent_count"),
                                    rs.getInt("good_count"),
                                    rs.getInt("medium_count"),
                                    rs.getInt("pass_count"),
                                    rs.getInt("fail_count"),
                                    studentCount));
                });
    }

    private long loadRosterCount(Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId) {
        Long count = jdbcTemplate.queryForObject(
                GradebookQuerySql.rosterCtes() + "SELECT COUNT(*) FROM roster",
                params(offeringId, teachingClassId, studentUserId),
                Long.class);
        return count == null ? 0 : count;
    }

    private GradebookPageView.GradeCellView toGradeCell(ResultSet rs) throws SQLException {
        boolean submitted = getNullableLong(rs, "submission_id") != null;
        Boolean applicable = rs.getObject("applicable", Boolean.class);
        Boolean fullyGraded = rs.getObject("fully_graded", Boolean.class);
        Boolean gradePublished = rs.getObject("grade_published", Boolean.class);
        SubmissionScoreSummaryView scoreSummary = submitted
                ? new SubmissionScoreSummaryView(
                        rs.getInt("auto_score"),
                        rs.getInt("manual_score"),
                        rs.getInt("final_score"),
                        rs.getInt("max_score"),
                        rs.getInt("pending_manual_count"),
                        rs.getInt("pending_programming_count"),
                        fullyGraded,
                        gradePublished)
                : null;
        return new GradebookPageView.GradeCellView(
                rs.getLong("assignment_id"),
                applicable,
                submitted,
                getNullableLong(rs, "submission_id"),
                getNullableInteger(rs, "attempt_no"),
                rs.getObject("submitted_at", OffsetDateTime.class),
                scoreSummary,
                getNullableInteger(rs, "final_score"),
                rs.getInt("max_score"),
                getNullableDouble(rs, "weighted_score"),
                fullyGraded,
                gradePublished);
    }

    private MapSqlParameterSource params(
            Long offeringId, @Nullable Long teachingClassId, @Nullable Long studentUserId) {
        return new MapSqlParameterSource()
                .addValue("offeringId", offeringId, Types.BIGINT)
                .addValue("teachingClassId", teachingClassId, Types.BIGINT)
                .addValue("studentUserId", studentUserId, Types.BIGINT)
                .addValue("filterByTeachingClass", teachingClassId != null, Types.BOOLEAN)
                .addValue("filterByStudentUser", studentUserId != null, Types.BOOLEAN);
    }

    private int normalize(long pageSize) {
        return (int) Math.max(pageSize, 1);
    }

    private int offset(long page, long pageSize) {
        long normalizedPage = Math.max(page, 1);
        return (int) ((normalizedPage - 1) * Math.max(pageSize, 1));
    }

    private double ratio(BigDecimal numerator, int denominator) {
        if (denominator <= 0 || numerator == null) {
            return 0.0;
        }
        return numerator
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
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

    private double average(BigDecimal total, int count, int scale) {
        if (count <= 0 || total == null) {
            return 0.0;
        }
        return total.divide(BigDecimal.valueOf(count), scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<GradebookReportView.ScoreBandView> zeroScoreBands(int population) {
        return toScoreBands(0, 0, 0, 0, population, population);
    }

    private List<GradebookReportView.ScoreBandView> toScoreBands(
            int excellent, int good, int medium, int pass, int fail, int population) {
        int[] counts = {excellent, good, medium, pass, fail};
        List<GradebookReportView.ScoreBandView> scoreBands = new ArrayList<>();
        for (int i = 0; i < SCORE_BANDS.size(); i++) {
            ScoreBandDefinition definition = SCORE_BANDS.get(i);
            scoreBands.add(new GradebookReportView.ScoreBandView(
                    definition.bandCode(),
                    definition.label(),
                    definition.minPercentInclusive(),
                    definition.maxPercentExclusive(),
                    counts[i],
                    ratio(counts[i], population)));
        }
        return List.copyOf(scoreBands);
    }

    @Nullable
    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    @Nullable
    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    @Nullable
    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).doubleValue();
    }

    record GradebookPageAggregate(
            GradebookPageView.ScopeView scope,
            GradebookPageView.SummaryView summary,
            List<GradebookPageView.AssignmentColumnView> assignmentColumns,
            List<GradebookPageView.StudentRowView> items,
            long total) {}

    record GradebookReportAggregate(
            GradebookPageView.ScopeView scope,
            GradebookReportView.OverviewView overview,
            List<GradebookReportView.AssignmentStatView> assignments,
            List<GradebookReportView.TeachingClassStatView> teachingClasses) {}

    private record ScoreBandDefinition(
            String bandCode, String label, int minPercentInclusive, Integer maxPercentExclusive) {}
}
