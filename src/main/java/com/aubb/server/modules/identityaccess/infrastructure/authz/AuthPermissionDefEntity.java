package com.aubb.server.modules.identityaccess.infrastructure.authz;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_permission_defs")
public class AuthPermissionDefEntity {

    private String code;

    private String name;

    private String domain;

    private String description;

    private Boolean sensitive;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
