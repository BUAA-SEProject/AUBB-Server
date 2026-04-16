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
@TableName("assignment_question_options")
public class AssignmentQuestionOptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentQuestionId;

    private Integer optionOrder;

    private String optionKey;

    private String content;

    private Boolean isCorrect;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
