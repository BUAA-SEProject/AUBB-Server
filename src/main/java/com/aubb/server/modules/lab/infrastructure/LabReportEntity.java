package com.aubb.server.modules.lab.infrastructure;

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
@TableName("lab_reports")
public class LabReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long labId;

    private Long offeringId;

    private Long teachingClassId;

    private Long studentUserId;

    private String status;

    private String reportContentText;

    private String teacherAnnotationText;

    private String teacherCommentText;

    private OffsetDateTime submittedAt;

    private OffsetDateTime reviewedAt;

    private OffsetDateTime publishedAt;

    private Long reviewerUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
