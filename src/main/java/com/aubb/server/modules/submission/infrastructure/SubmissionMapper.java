package com.aubb.server.modules.submission.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubmissionMapper extends BaseMapper<SubmissionEntity> {

    @Select("""
            SELECT 1
            FROM (
                SELECT pg_advisory_xact_lock((#{assignmentId}::bigint << 32) | (#{userId}::bigint & 4294967295))
            ) lock_holder
            """)
    int acquireSubmissionAttemptLock(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

    @Select({
        "<script>",
        "SELECT COUNT(*)",
        "FROM (",
        "  SELECT s.submitter_user_id",
        "  FROM submissions s",
        "  WHERE s.assignment_id = #{assignmentId}",
        "  <if test='submitterUserId != null'>",
        "    AND s.submitter_user_id = #{submitterUserId}",
        "  </if>",
        "  GROUP BY s.submitter_user_id",
        ") ranked",
        "</script>"
    })
    long countLatestSubmissionsPage(
            @Param("assignmentId") Long assignmentId, @Param("submitterUserId") Long submitterUserId);

    @Select({
        "<script>",
        "WITH ranked AS (",
        "  SELECT",
        "    s.id,",
        "    s.submission_no,",
        "    s.assignment_id,",
        "    s.offering_id,",
        "    s.teaching_class_id,",
        "    s.submitter_user_id,",
        "    s.attempt_no,",
        "    s.status,",
        "    s.content_text,",
        "    s.submitted_at,",
        "    s.created_at,",
        "    s.updated_at,",
        "    ROW_NUMBER() OVER (PARTITION BY s.submitter_user_id ORDER BY s.submitted_at DESC, s.id DESC) AS rn",
        "  FROM submissions s",
        "  WHERE s.assignment_id = #{assignmentId}",
        "  <if test='submitterUserId != null'>",
        "    AND s.submitter_user_id = #{submitterUserId}",
        "  </if>",
        ")",
        "SELECT",
        "  id,",
        "  submission_no,",
        "  assignment_id,",
        "  offering_id,",
        "  teaching_class_id,",
        "  submitter_user_id,",
        "  attempt_no,",
        "  status,",
        "  content_text,",
        "  submitted_at,",
        "  created_at,",
        "  updated_at",
        "FROM ranked",
        "WHERE rn = 1",
        "ORDER BY submitted_at DESC, id DESC",
        "LIMIT #{limit} OFFSET #{offset}",
        "</script>"
    })
    List<SubmissionEntity> selectLatestSubmissionsPage(
            @Param("assignmentId") Long assignmentId,
            @Param("submitterUserId") Long submitterUserId,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
