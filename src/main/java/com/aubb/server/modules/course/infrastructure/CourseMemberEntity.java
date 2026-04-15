package com.aubb.server.modules.course.infrastructure;

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
@TableName("course_members")
public class CourseMemberEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long teachingClassId;

    private Long userId;

    private String memberRole;

    private String memberStatus;

    private String sourceType;

    private String remark;

    private OffsetDateTime joinedAt;

    private OffsetDateTime leftAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
