package com.aubb.server.modules.identityaccess.application.user.view;

import com.aubb.server.modules.identityaccess.domain.profile.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.profile.AcademicProfileStatus;

public record AcademicProfileView(
        Long id,
        Long userId,
        String academicId,
        String realName,
        AcademicIdentityType identityType,
        AcademicProfileStatus profileStatus,
        String phone) {}
