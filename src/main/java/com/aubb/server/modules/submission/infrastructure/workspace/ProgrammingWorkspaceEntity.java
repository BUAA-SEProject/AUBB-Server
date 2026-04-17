package com.aubb.server.modules.submission.infrastructure.workspace;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("programming_workspaces")
public class ProgrammingWorkspaceEntity {

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

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastStdinText;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
