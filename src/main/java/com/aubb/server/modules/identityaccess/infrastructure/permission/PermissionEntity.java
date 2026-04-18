package com.aubb.server.modules.identityaccess.infrastructure.permission;

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
@TableName("permissions")
public class PermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String resourceType;

    private String action;

    private String description;

    private Boolean sensitive;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
