package com.aubb.server.modules.judge.infrastructure.sample;

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
@TableName("programming_sample_runs")
public class ProgrammingSampleRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assignmentId;

    private Long assignmentQuestionId;

    private Long userId;

    private String programmingLanguage;

    private String codeText;

    private String artifactIdsJson;

    private String entryFilePath;

    private String sourceFilesJson;

    private String sourceDirectoriesJson;

    private String sourceSnapshotObjectKey;

    private Long workspaceRevisionId;

    private String inputMode;

    private String stdinText;

    private String expectedStdout;

    private String status;

    private String verdict;

    private String stdoutText;

    private String stderrText;

    private String resultSummary;

    private String errorMessage;

    private Long timeMillis;

    private Long memoryBytes;

    private String detailReportJson;

    private String detailReportObjectKey;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
