package com.aubb.server.modules.identityaccess.infrastructure.auth;

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
@TableName("auth_sessions")
public class AuthSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String refreshTokenHash;

    private OffsetDateTime refreshTokenExpiresAt;

    private OffsetDateTime revokedAt;

    private String revokedReason;

    private Long revokedByUserId;

    private OffsetDateTime lastAccessIssuedAt;

    private OffsetDateTime lastRefreshedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
