package com.aubb.server.modules.identityaccess.application.user;

public record UserDirectoryEntryView(
        Long id,
        String username,
        String displayName,
        String email,
        String phone,
        AcademicProfileView academicProfile) {}
