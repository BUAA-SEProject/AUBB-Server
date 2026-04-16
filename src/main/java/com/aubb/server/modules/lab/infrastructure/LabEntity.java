package com.aubb.server.modules.lab.infrastructure;

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
@TableName("labs")
public class LabEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teachingClassId;

    private String title;

    private String description;

    private String status;

    private OffsetDateTime publishedAt;

    private OffsetDateTime closedAt;

    private Long createdByUserId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
