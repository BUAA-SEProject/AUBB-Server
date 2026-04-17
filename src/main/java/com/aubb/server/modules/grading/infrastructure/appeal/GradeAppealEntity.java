package com.aubb.server.modules.grading.infrastructure.appeal;

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
@TableName("grade_appeals")
public class GradeAppealEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teachingClassId;

    private Long assignmentId;

    private Long submissionId;

    private Long submissionAnswerId;

    private Long studentUserId;

    private String status;

    private String appealReason;

    private String responseText;

    private Integer resolvedScore;

    private Long respondedByUserId;

    private OffsetDateTime respondedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
