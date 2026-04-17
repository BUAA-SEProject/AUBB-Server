package com.aubb.server.modules.submission.infrastructure.workspace;

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
@TableName("programming_workspace_revisions")
public class ProgrammingWorkspaceRevisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private Long assignmentId;

    private Long assignmentQuestionId;

    private Long userId;

    private Long revisionNo;

    private String revisionKind;

    private String revisionMessage;

    private String programmingLanguage;

    private String codeText;

    private String artifactIdsJson;

    private String entryFilePath;

    private String sourceFilesJson;

    private String sourceDirectoriesJson;

    private String lastStdinText;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
