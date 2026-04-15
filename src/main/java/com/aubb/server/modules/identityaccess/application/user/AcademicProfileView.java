package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.modules.identityaccess.domain.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.AcademicProfileStatus;

public record AcademicProfileView(
        Long id,
        Long userId,
        String academicId,
        String realName,
        AcademicIdentityType identityType,
        AcademicProfileStatus profileStatus,
        String phone) {}
