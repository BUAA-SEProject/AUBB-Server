package com.aubb.server.modules.grading.application.gradebook;

final class GradebookQuerySql {

    private GradebookQuerySql() {}

    static String rosterCtes() {
        return """
                WITH roster_candidates AS (
                    SELECT
                        cm.user_id,
                        cm.teaching_class_id,
                        ROW_NUMBER() OVER (
                            PARTITION BY cm.user_id
                            ORDER BY
                                CASE WHEN cm.teaching_class_id IS NULL THEN 1 ELSE 0 END,
                                COALESCE(cm.teaching_class_id, 9223372036854775807),
                                cm.id
                        ) AS row_num
                    FROM course_members cm
                    WHERE cm.offering_id = :offeringId
                      AND cm.member_role = 'STUDENT'
                      AND cm.member_status = 'ACTIVE'
                      AND (:filterByTeachingClass = FALSE OR cm.teaching_class_id = :teachingClassId)
                      AND (:filterByStudentUser = FALSE OR cm.user_id = :studentUserId)
                ),
                roster AS (
                    SELECT
                        candidate.user_id,
                        candidate.teaching_class_id,
                        users.username,
                        users.display_name,
                        teaching_classes.class_code,
                        teaching_classes.class_name
                    FROM roster_candidates candidate
                    JOIN users ON users.id = candidate.user_id
                    LEFT JOIN teaching_classes ON teaching_classes.id = candidate.teaching_class_id
                    WHERE candidate.row_num = 1
                )
                """;
    }

    static String assignmentBackedCtes() {
        return rosterCtes() + """
                        ,
                        assignment_defs AS (
                            SELECT
                                assignments.id AS assignment_id,
                                assignments.teaching_class_id,
                                teaching_classes.class_name AS teaching_class_name,
                                assignments.title,
                                assignments.status,
                                assignments.open_at,
                                assignments.due_at,
                                COALESCE(assignments.grade_weight, 100) AS grade_weight,
                                section_scores.max_score,
                                (assignments.grade_published_at IS NOT NULL) AS grade_published
                            FROM assignments
                            JOIN (
                                SELECT assignment_id, SUM(total_score) AS max_score
                                FROM assignment_sections
                                GROUP BY assignment_id
                            ) section_scores ON section_scores.assignment_id = assignments.id
                            LEFT JOIN teaching_classes ON teaching_classes.id = assignments.teaching_class_id
                            WHERE assignments.offering_id = :offeringId
                              AND assignments.status <> 'DRAFT'
                              AND (
                                  :filterByTeachingClass = FALSE
                                  OR assignments.teaching_class_id IS NULL
                                  OR assignments.teaching_class_id = :teachingClassId
                              )
                        ),
                        latest_submissions AS (
                            SELECT DISTINCT ON (submissions.assignment_id, submissions.submitter_user_id)
                                submissions.id AS submission_id,
                                submissions.assignment_id,
                                submissions.submitter_user_id AS user_id,
                                submissions.attempt_no,
                                submissions.submitted_at
                            FROM submissions
                            JOIN roster ON roster.user_id = submissions.submitter_user_id
                            JOIN assignment_defs ON assignment_defs.assignment_id = submissions.assignment_id
                            ORDER BY
                                submissions.assignment_id,
                                submissions.submitter_user_id,
                                submissions.submitted_at DESC,
                                submissions.id DESC
                        ),
                        submission_scores AS (
                            SELECT
                                submission_answers.submission_id,
                                COALESCE(SUM(COALESCE(submission_answers.auto_score, 0)), 0) AS auto_score,
                                COALESCE(SUM(COALESCE(submission_answers.manual_score, 0)), 0) AS manual_score,
                                COALESCE(SUM(COALESCE(submission_answers.final_score, 0)), 0) AS final_score,
                                COUNT(*) FILTER (WHERE submission_answers.grading_status = 'PENDING_MANUAL')
                                    AS pending_manual_count,
                                COUNT(*) FILTER (WHERE submission_answers.grading_status = 'PENDING_PROGRAMMING_JUDGE')
                                    AS pending_programming_count
                            FROM submission_answers
                            JOIN latest_submissions ON latest_submissions.submission_id = submission_answers.submission_id
                            GROUP BY submission_answers.submission_id
                        ),
                        grade_matrix AS (
                            SELECT
                                roster.user_id,
                                roster.username,
                                roster.display_name,
                                roster.teaching_class_id,
                                roster.class_code,
                                roster.class_name,
                                assignment_defs.assignment_id,
                                assignment_defs.teaching_class_id AS assignment_teaching_class_id,
                                assignment_defs.title,
                                assignment_defs.status,
                                assignment_defs.open_at,
                                assignment_defs.due_at,
                                assignment_defs.grade_weight,
                                assignment_defs.max_score,
                                assignment_defs.grade_published,
                                (assignment_defs.teaching_class_id IS NULL
                                    OR assignment_defs.teaching_class_id = roster.teaching_class_id) AS applicable,
                                latest_submissions.submission_id,
                                latest_submissions.attempt_no,
                                latest_submissions.submitted_at,
                                submission_scores.auto_score,
                                submission_scores.manual_score,
                                submission_scores.final_score,
                                submission_scores.pending_manual_count,
                                submission_scores.pending_programming_count,
                                CASE
                                    WHEN (assignment_defs.teaching_class_id IS NULL
                                            OR assignment_defs.teaching_class_id = roster.teaching_class_id)
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
                                            OR assignment_defs.teaching_class_id = roster.teaching_class_id)
                                            AND latest_submissions.submission_id IS NOT NULL
                                        THEN (
                                            submission_scores.pending_manual_count = 0
                                            AND submission_scores.pending_programming_count = 0
                                        )
                                    ELSE NULL
                                END AS fully_graded
                            FROM roster
                            CROSS JOIN assignment_defs
                            LEFT JOIN latest_submissions
                                ON latest_submissions.assignment_id = assignment_defs.assignment_id
                               AND latest_submissions.user_id = roster.user_id
                            LEFT JOIN submission_scores
                                ON submission_scores.submission_id = latest_submissions.submission_id
                        ),
                        row_metrics AS (
                            SELECT
                                grade_matrix.user_id,
                                grade_matrix.username,
                                grade_matrix.display_name,
                                grade_matrix.teaching_class_id,
                                grade_matrix.class_code,
                                grade_matrix.class_name,
                                COALESCE(SUM(
                                    CASE
                                        WHEN grade_matrix.applicable AND grade_matrix.final_score IS NOT NULL
                                            THEN grade_matrix.final_score
                                        ELSE 0
                                    END
                                ), 0) AS total_final_score,
                                COALESCE(SUM(
                                    CASE
                                        WHEN grade_matrix.applicable THEN grade_matrix.max_score
                                        ELSE 0
                                    END
                                ), 0) AS total_max_score,
                                COALESCE(SUM(
                                    CASE
                                        WHEN grade_matrix.applicable THEN grade_matrix.grade_weight
                                        ELSE 0
                                    END
                                ), 0) AS total_weight,
                                COALESCE(SUM(
                                    CASE
                                        WHEN grade_matrix.applicable AND grade_matrix.weighted_score IS NOT NULL
                                            THEN grade_matrix.weighted_score
                                        ELSE 0
                                    END
                                ), 0) AS total_weighted_score,
                                COUNT(*) FILTER (
                                    WHERE grade_matrix.applicable
                                      AND grade_matrix.submission_id IS NOT NULL
                                ) AS submitted_assignment_count,
                                COUNT(*) FILTER (
                                    WHERE grade_matrix.applicable
                                      AND grade_matrix.fully_graded IS TRUE
                                ) AS graded_assignment_count,
                                COUNT(*) FILTER (
                                    WHERE grade_matrix.applicable
                                      AND grade_matrix.submission_id IS NOT NULL
                                      AND grade_matrix.grade_published
                                ) AS published_assignment_count,
                                COUNT(*) FILTER (WHERE grade_matrix.applicable) AS applicable_assignment_count
                            FROM grade_matrix
                            GROUP BY
                                grade_matrix.user_id,
                                grade_matrix.username,
                                grade_matrix.display_name,
                                grade_matrix.teaching_class_id,
                                grade_matrix.class_code,
                                grade_matrix.class_name
                        ),
                        scored_rows AS (
                            SELECT
                                row_metrics.*,
                                CASE
                                    WHEN row_metrics.total_weight > 0
                                        THEN LEAST(GREATEST(
                                            ROUND(row_metrics.total_weighted_score / row_metrics.total_weight::numeric, 4),
                                            0
                                        ), 1)
                                    WHEN row_metrics.total_max_score > 0
                                        THEN LEAST(GREATEST(
                                            ROUND(row_metrics.total_final_score::numeric / row_metrics.total_max_score::numeric, 4),
                                            0
                                        ), 1)
                                    ELSE 0
                                END AS overall_score_rate
                            FROM row_metrics
                        ),
                        ranked_rows AS (
                            SELECT
                                scored_rows.*,
                                RANK() OVER (
                                    ORDER BY
                                        scored_rows.overall_score_rate DESC,
                                        scored_rows.total_final_score DESC,
                                        scored_rows.total_weighted_score DESC
                                ) AS offering_rank,
                                RANK() OVER (
                                    PARTITION BY COALESCE(scored_rows.teaching_class_id, -1)
                                    ORDER BY
                                        scored_rows.overall_score_rate DESC,
                                        scored_rows.total_final_score DESC,
                                        scored_rows.total_weighted_score DESC
                                ) AS teaching_class_rank
                            FROM scored_rows
                        )
                        """;
    }
}
