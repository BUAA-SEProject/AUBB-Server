package com.aubb.server.modules.identityaccess.infrastructure.authz;

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
@TableName("auth_groups")
public class AuthGroupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String scopeType;

    private Long scopeRefId;

    private String displayName;

    private Boolean managedBySystem;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
