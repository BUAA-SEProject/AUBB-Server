package com.aubb.server.modules.course.infrastructure.teaching;

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
@TableName("teaching_classes")
public class TeachingClassEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private String classCode;

    private String className;

    private Integer entryYear;

    private Long orgClassUnitId;

    private Integer capacity;

    private String status;

    private String scheduleSummary;

    private Boolean announcementEnabled;

    private Boolean discussionEnabled;

    private Boolean resourceEnabled;

    private Boolean labEnabled;

    private Boolean assignmentEnabled;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
