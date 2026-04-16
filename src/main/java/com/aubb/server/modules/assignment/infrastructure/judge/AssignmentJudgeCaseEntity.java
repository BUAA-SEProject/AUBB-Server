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
@TableName("assignment_judge_cases")
public class AssignmentJudgeCaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;

    private Integer caseOrder;

    private String stdinText;

    private String expectedStdout;

    private Integer score;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
