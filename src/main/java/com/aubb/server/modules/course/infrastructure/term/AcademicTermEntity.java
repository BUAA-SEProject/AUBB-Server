package com.aubb.server.modules.course.infrastructure.term;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("academic_terms")
public class AcademicTermEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String termCode;

    private String termName;

    private String schoolYear;

    private String semester;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
