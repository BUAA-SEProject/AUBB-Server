package com.aubb.server.application.platformconfig;

import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformConfigView(
        Long id,
        String platformName,
        String platformShortName,
        String logoUrl,
        String footerText,
        String defaultHomePath,
        String themeKey,
        String loginNotice,
        Map<String, Object> moduleFlags,
        Long updatedByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
