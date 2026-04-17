package com.aubb.server.modules.identityaccess.infrastructure.membership;

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
@TableName("user_org_memberships")
public class UserOrgMembershipEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long orgUnitId;

    private String membershipType;

    private String membershipStatus;

    private String sourceType;

    private OffsetDateTime startAt;

    private OffsetDateTime endAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
