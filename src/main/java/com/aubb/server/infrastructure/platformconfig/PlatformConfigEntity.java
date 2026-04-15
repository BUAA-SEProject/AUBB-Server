package com.aubb.server.infrastructure.platformconfig;

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
@TableName(value = "platform_configs", autoResultMap = true)
public class PlatformConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platformName;

    private String platformShortName;

    private String logoUrl;

    private String footerText;

    private String defaultHomePath;

    private String themeKey;

    private String loginNotice;

    @TableField(typeHandler = PostgreSqlJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> moduleFlags;

    private Long updatedByUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
