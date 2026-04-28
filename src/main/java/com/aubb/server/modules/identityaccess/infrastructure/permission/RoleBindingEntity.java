package com.aubb.server.modules.identityaccess.infrastructure.permission;

import com.aubb.server.infrastructure.persistence.PostgreSqlJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

@Getter
@Setter
@TableName(value = "role_bindings", autoResultMap = true)
public class RoleBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roleId;

    private String scopeType;

    private Long scopeId;

    @TableField(typeHandler = PostgreSqlJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> constraintsJson;

    private String status;

    private OffsetDateTime effectiveFrom;

    private OffsetDateTime effectiveTo;

    private Long grantedBy;

    private String sourceType;

    private Long sourceRefId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
