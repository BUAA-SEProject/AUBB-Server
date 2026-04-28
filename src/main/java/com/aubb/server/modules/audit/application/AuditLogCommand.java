package com.aubb.server.modules.audit.application;

import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditDecision;
import com.aubb.server.modules.audit.domain.AuditResult;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record AuditLogCommand(
        Long actorUserId,
        AuditAction action,
        String targetType,
        String targetId,
        AuditResult result,
        String scopeType,
        Long scopeId,
        AuditDecision decision,
        Map<String, Object> metadata) {

    public AuditLogCommand {
        if (action == null) {
            throw new IllegalArgumentException("Audit action must not be null");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("Audit target type must not be blank");
        }
        if (result == null) {
            throw new IllegalArgumentException("Audit result must not be null");
        }
        targetType = targetType.trim();
        targetId = targetId == null || targetId.isBlank() ? null : targetId.trim();
        scopeType = scopeType == null || scopeType.isBlank()
                ? null
                : scopeType.trim().toLowerCase(Locale.ROOT);
        metadata = metadata == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
