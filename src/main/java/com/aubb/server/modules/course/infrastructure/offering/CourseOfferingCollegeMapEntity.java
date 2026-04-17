package com.aubb.server.modules.course.infrastructure.offering;

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
@TableName("course_offering_college_maps")
public class CourseOfferingCollegeMapEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long offeringId;

    private Long collegeUnitId;

    private String relationType;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
