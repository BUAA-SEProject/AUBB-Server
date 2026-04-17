package com.aubb.server.modules.grading.infrastructure.snapshot;

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
@TableName("grade_publish_snapshots")
public class GradePublishSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long publishBatchId;

    private Long assignmentId;

    private Long offeringId;

    private Long teachingClassId;

    private Long studentUserId;

    private Long submissionId;

    private String submissionNo;

    private Integer attemptNo;

    private OffsetDateTime submittedAt;

    private Integer totalFinalScore;

    private Integer totalMaxScore;

    private Integer autoScoredScore;

    private Integer manualScoredScore;

    private Boolean fullyGraded;

    private String snapshotJson;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
