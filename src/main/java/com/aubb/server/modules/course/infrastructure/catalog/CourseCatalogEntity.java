package com.aubb.server.modules.course.infrastructure.catalog;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("course_catalogs")
public class CourseCatalogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseCode;

    private String courseName;

    private String courseType;

    private BigDecimal credit;

    private Integer totalHours;

    private Long departmentUnitId;

    private String description;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
