package com.aubb.server.modules.judge.infrastructure;

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
@TableName("judge_jobs")
public class JudgeJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;

    private Long submissionAnswerId;

    private Long assignmentId;

    private Long assignmentQuestionId;

    private Long offeringId;

    private Long teachingClassId;

    private Long submitterUserId;

    private Long requestedByUserId;

    private String triggerType;

    private String status;

    private String engineCode;

    private String engineJobRef;

    private String resultSummary;

    private String verdict;

    private Integer totalCaseCount;

    private Integer passedCaseCount;

    private Integer score;

    private Integer maxScore;

    private String stdoutExcerpt;

    private String stderrExcerpt;

    private Long timeMillis;

    private Long memoryBytes;

    private String errorMessage;

    private String caseResultsJson;

    private String detailReportJson;

    private String detailReportObjectKey;

    private String sourceSnapshotObjectKey;

    private String artifactManifestObjectKey;

    private String artifactTraceJson;

    private OffsetDateTime queuedAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
