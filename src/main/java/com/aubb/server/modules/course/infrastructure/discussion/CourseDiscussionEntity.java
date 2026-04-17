package com.aubb.server.modules.course.infrastructure.discussion;

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
@TableName("course_discussions")
public class CourseDiscussionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teachingClassId;

    private Long createdByUserId;

    private String title;

    private Boolean locked;

    private OffsetDateTime lastActivityAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
