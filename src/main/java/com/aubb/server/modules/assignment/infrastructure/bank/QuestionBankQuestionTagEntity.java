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
@TableName("question_bank_question_tags")
public class QuestionBankQuestionTagEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long tagId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
