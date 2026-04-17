package com.aubb.server.modules.assignment.infrastructure.bank;

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
@TableName("question_bank_question_options")
public class QuestionBankQuestionOptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Integer optionOrder;

    private String optionKey;

    private String content;

    private Boolean isCorrect;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
