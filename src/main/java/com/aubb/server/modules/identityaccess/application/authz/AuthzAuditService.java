package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthzAuditService {

    private static final String AUDITED_REQUEST_ATTRIBUTE =
            AuthzAuditService.class.getName() + ".AUTHZ_DENIED_RECORDED";

    private final AuditLogApplicationService auditLogApplicationService;

    public void recordDenied(HttpServletRequest request, String reasonCode) {
        if (request == null || Boolean.TRUE.equals(request.getAttribute(AUDITED_REQUEST_ATTRIBUTE))) {
            return;
        }
        request.setAttribute(AUDITED_REQUEST_ATTRIBUTE, Boolean.TRUE);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUserPrincipal principal = resolvePrincipal(authentication);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", request.getMethod());
        metadata.put("path", request.getRequestURI());
        metadata.put("reason", reasonCode);

        try {
            auditLogApplicationService.record(
                    principal == null ? null : principal.getUserId(),
                    AuditAction.AUTHZ_DENIED,
                    "AUTHORIZATION",
                    request.getRequestURI(),
                    AuditResult.FAILURE,
                    metadata);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to record AUTHZ_DENIED audit for {} {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception);
        }
    }

    private AuthenticatedUserPrincipal resolvePrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal
                ? authenticatedUserPrincipal
                : null;
    }
}
