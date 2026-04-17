package com.aubb.server.modules.notification.infrastructure;

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
@TableName(value = "notifications", autoResultMap = true)
public class NotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String type;

    private String title;

    private String body;

    private Long actorUserId;

    private String targetType;

    private String targetId;

    private Long offeringId;

    private Long teachingClassId;

    @TableField(typeHandler = PostgreSqlJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> metadata;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
