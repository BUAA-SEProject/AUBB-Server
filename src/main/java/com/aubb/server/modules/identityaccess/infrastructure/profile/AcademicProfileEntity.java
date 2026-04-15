package com.aubb.server.modules.identityaccess.infrastructure.profile;

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
@TableName("academic_profiles")
public class AcademicProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String academicId;

    private String realName;

    private String identityType;

    private String profileStatus;

    private String phone;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
