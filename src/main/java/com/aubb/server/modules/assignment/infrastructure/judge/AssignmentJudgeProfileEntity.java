package com.aubb.server.modules.assignment.infrastructure.judge;

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
@TableName("assignment_judge_profiles")
public class AssignmentJudgeProfileEntity {

    @TableId(value = "assignment_id", type = IdType.INPUT)
    private Long assignmentId;

    private String sourceType;

    private String language;

    private String entryFileName;

    private Integer timeLimitMs;

    private Integer memoryLimitMb;

    private Integer outputLimitKb;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
