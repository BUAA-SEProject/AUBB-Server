package com.aubb.server.modules.submission.infrastructure.answer;

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
@TableName("submission_answers")
public class SubmissionAnswerEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;

    private Long assignmentQuestionId;

    private String answerText;

    private String answerPayloadJson;

    private Integer autoScore;

    private Integer manualScore;

    private Integer finalScore;

    private String gradingStatus;

    private String feedbackText;

    private Long gradedByUserId;

    private OffsetDateTime gradedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
