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
@TableName("lab_report_attachments")
public class LabReportAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long labId;

    private Long offeringId;

    private Long teachingClassId;

    private Long labReportId;

    private Long uploaderUserId;

    private String objectKey;

    private String originalFilename;

    private String contentType;

    private Long sizeBytes;

    private OffsetDateTime uploadedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
