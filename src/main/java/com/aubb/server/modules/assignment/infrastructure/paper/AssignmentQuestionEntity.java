package com.aubb.server.modules.assignment.infrastructure.paper;

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
@TableName("assignment_questions")
public class AssignmentQuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;

    private Long assignmentSectionId;

    private Long sourceQuestionId;

    private Integer questionOrder;

    private String title;

    private String promptText;

    private String questionType;

    private Integer score;

    private String configJson;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
