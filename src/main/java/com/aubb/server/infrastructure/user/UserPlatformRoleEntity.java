package com.aubb.server.infrastructure.user;

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
@TableName("user_platform_roles")
public class UserPlatformRoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String roleCode;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
