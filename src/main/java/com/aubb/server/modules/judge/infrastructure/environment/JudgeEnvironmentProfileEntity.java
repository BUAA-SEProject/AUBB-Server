package com.aubb.server.modules.judge.infrastructure.environment;

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
@TableName("judge_environment_profiles")
public class JudgeEnvironmentProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private String profileCode;

    private String normalizedCode;

    private String profileName;

    private String description;

    private String programmingLanguage;

    private String languageVersion;

    private String workingDirectory;

    private String initScript;

    private String compileCommand;

    private String runCommand;

    private String environmentVariablesJson;

    private Integer cpuRateLimit;

    private String supportFilesJson;

    private Long createdByUserId;

    private Long archivedByUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private OffsetDateTime archivedAt;
}
