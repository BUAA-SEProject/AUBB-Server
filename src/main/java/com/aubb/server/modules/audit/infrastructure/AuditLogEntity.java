package com.aubb.server.modules.audit.infrastructure;

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
@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorUserId;

    private String action;

    private String targetType;

    private String targetId;

    private String result;

    private String requestId;

    private String ip;

    private String scopeType;

    private Long scopeId;

    private String decision;

    private String userAgent;

    @TableField(typeHandler = PostgreSqlJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> metadata;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
