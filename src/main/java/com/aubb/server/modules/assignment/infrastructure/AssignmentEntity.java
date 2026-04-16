package com.aubb.server.modules.assignment.infrastructure;

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
@TableName("assignments")
public class AssignmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teachingClassId;

    private String title;

    private String description;

    private String status;

    private OffsetDateTime openAt;

    private OffsetDateTime dueAt;

    private Integer maxSubmissions;

    private Integer gradeWeight;

    private OffsetDateTime publishedAt;

    private OffsetDateTime closedAt;

    private OffsetDateTime gradePublishedAt;

    private Long gradePublishedByUserId;

    private Long createdByUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
