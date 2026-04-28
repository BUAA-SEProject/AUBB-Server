package com.aubb.server.modules.course.infrastructure.resource;

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
@TableName("course_resources")
public class CourseResourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teachingClassId;

    private Long uploaderUserId;

    private String title;

    private String objectKey;

    private String originalFilename;

    private String contentType;

    private Long sizeBytes;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
