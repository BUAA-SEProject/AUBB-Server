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
@TableName("question_bank_questions")
public class QuestionBankQuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long createdByUserId;

    private Long archivedByUserId;

    private String title;

    private String promptText;

    private String questionType;

    private Integer defaultScore;

    private String configJson;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private OffsetDateTime archivedAt;
}
