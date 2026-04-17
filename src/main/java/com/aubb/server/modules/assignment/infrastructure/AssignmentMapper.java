package com.aubb.server.modules.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssignmentMapper extends BaseMapper<AssignmentEntity> {

    @Select({
        "<script>",
        "SELECT COUNT(*)",
        "FROM assignments a",
        "WHERE a.status &lt;&gt; 'DRAFT'",
        "<if test='offeringId != null'>",
        "  AND a.offering_id = #{offeringId}",
        "</if>",
        "<trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
        "  <if test='fullOfferingIds != null and fullOfferingIds.size() > 0'>",
        "    OR a.offering_id IN",
        "    <foreach collection='fullOfferingIds' item='fullOfferingId' open='(' separator=',' close=')'>",
        "      #{fullOfferingId}",
        "    </foreach>",
        "  </if>",
        "  OR (",
        "    a.teaching_class_id IS NULL",
        "    AND EXISTS (",
        "      SELECT 1",
        "      FROM course_members cm",
        "      WHERE cm.user_id = #{userId}",
        "        AND cm.offering_id = a.offering_id",
        "        AND cm.member_status = 'ACTIVE'",
        "    )",
        "  )",
        "  OR (",
        "    a.teaching_class_id IS NOT NULL",
        "    AND EXISTS (",
        "      SELECT 1",
        "      FROM course_members cm",
        "      WHERE cm.user_id = #{userId}",
        "        AND cm.offering_id = a.offering_id",
        "        AND cm.teaching_class_id = a.teaching_class_id",
        "        AND cm.member_status = 'ACTIVE'",
        "    )",
        "  )",
        "</trim>",
        "</script>"
    })
    long countVisibleAssignmentsForUser(
            @Param("userId") Long userId,
            @Param("offeringId") Long offeringId,
            @Param("fullOfferingIds") Collection<Long> fullOfferingIds);

    @Select({
        "<script>",
        "SELECT a.*",
        "FROM assignments a",
        "WHERE a.status &lt;&gt; 'DRAFT'",
        "<if test='offeringId != null'>",
        "  AND a.offering_id = #{offeringId}",
        "</if>",
        "<trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
        "  <if test='fullOfferingIds != null and fullOfferingIds.size() > 0'>",
        "    OR a.offering_id IN",
        "    <foreach collection='fullOfferingIds' item='fullOfferingId' open='(' separator=',' close=')'>",
        "      #{fullOfferingId}",
        "    </foreach>",
        "  </if>",
        "  OR (",
        "    a.teaching_class_id IS NULL",
        "    AND EXISTS (",
        "      SELECT 1",
        "      FROM course_members cm",
        "      WHERE cm.user_id = #{userId}",
        "        AND cm.offering_id = a.offering_id",
        "        AND cm.member_status = 'ACTIVE'",
        "    )",
        "  )",
        "  OR (",
        "    a.teaching_class_id IS NOT NULL",
        "    AND EXISTS (",
        "      SELECT 1",
        "      FROM course_members cm",
        "      WHERE cm.user_id = #{userId}",
        "        AND cm.offering_id = a.offering_id",
        "        AND cm.teaching_class_id = a.teaching_class_id",
        "        AND cm.member_status = 'ACTIVE'",
        "    )",
        "  )",
        "</trim>",
        "ORDER BY a.open_at ASC, a.id ASC",
        "LIMIT #{limit} OFFSET #{offset}",
        "</script>"
    })
    List<AssignmentEntity> selectVisibleAssignmentsForUserPage(
            @Param("userId") Long userId,
            @Param("offeringId") Long offeringId,
            @Param("fullOfferingIds") Collection<Long> fullOfferingIds,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
