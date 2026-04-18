package com.aubb.server.modules.audit.application;

import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogView(
        Long id,
        Long actorUserId,
        String action,
        String targetType,
        String targetId,
        String result,
        String requestId,
        String ip,
        String scopeType,
        Long scopeId,
        String decision,
        String userAgent,
        Map<String, Object> metadata,
        OffsetDateTime createdAt) {}
