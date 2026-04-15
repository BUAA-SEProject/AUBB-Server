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
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long primaryOrgUnitId;

    private String username;

    private String displayName;

    private String email;

    private String phone;

    private String passwordHash;

    private String accountStatus;

    private Integer failedLoginAttempts;

    private OffsetDateTime lockedUntil;

    private OffsetDateTime expiresAt;

    private OffsetDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
