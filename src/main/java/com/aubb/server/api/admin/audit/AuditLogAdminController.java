package com.aubb.server.api.admin.audit;

import com.aubb.server.application.audit.AuditLogApplicationService;
import com.aubb.server.application.audit.AuditLogView;
import com.aubb.server.common.api.PageResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogAdminController {

    private final AuditLogApplicationService auditLogApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCHOOL_ADMIN')")
    public PageResponse<AuditLogView> list(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return auditLogApplicationService.search(actorUserId, action, targetType, startAt, endAt, page, pageSize);
    }
}
