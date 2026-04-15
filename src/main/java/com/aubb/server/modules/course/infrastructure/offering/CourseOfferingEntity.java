package com.aubb.server.modules.course.infrastructure.offering;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("course_offerings")
public class CourseOfferingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long catalogId;

    private Long termId;

    private String offeringCode;

    private String offeringName;

    private Long primaryCollegeUnitId;

    private Long orgCourseUnitId;

    private String deliveryMode;

    private String language;

    private Integer capacity;

    private Integer selectedCount;

    private String intro;

    private String status;

    private OffsetDateTime publishAt;

    private OffsetDateTime startAt;

    private OffsetDateTime endAt;

    private OffsetDateTime archivedAt;

    private Long createdByUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
