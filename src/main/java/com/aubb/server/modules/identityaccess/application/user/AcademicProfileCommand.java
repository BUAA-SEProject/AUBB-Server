package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.modules.identityaccess.domain.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.AcademicProfileStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcademicProfileCommand(
        @NotBlank String academicId,
        @NotBlank String realName,
        @NotNull AcademicIdentityType identityType,
        @NotNull AcademicProfileStatus profileStatus,
        String phone) {}
