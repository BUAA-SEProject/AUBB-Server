package com.aubb.server.modules.submission.infrastructure;

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
@TableName("submissions")
public class SubmissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String submissionNo;

    private Long assignmentId;

    private Long offeringId;

    private Long teachingClassId;

    private Long submitterUserId;

    private Integer attemptNo;

    private String status;

    private String contentText;

    private OffsetDateTime submittedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
