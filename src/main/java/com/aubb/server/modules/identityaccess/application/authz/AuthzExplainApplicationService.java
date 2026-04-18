package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedPrincipalLoader;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzExplainView;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzExplainView.AuthzGrantView;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthzExplainApplicationService {

    private final AuthorizationService authorizationService;
    private final AuthenticatedPrincipalLoader authenticatedPrincipalLoader;
    private final AuthzScopeResolutionService authzScopeResolutionService;

    @Transactional(readOnly = true)
    public AuthzExplainView explain(
            Long userId,
            PermissionCode permission,
            AuthorizationScopeType scopeType,
            Long scopeRefId,
            AuthenticatedUserPrincipal principal) {
        ScopeRef scope = authzScopeResolutionService.resolveScope(scopeType, scopeRefId);
        requireExplainPermission(principal, scope);
        AuthenticatedUserPrincipal subject = authenticatedPrincipalLoader.loadPrincipal(userId);
        if (subject == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "目标用户不存在或不可用");
        }
        AuthorizationDecision decision =
                authorizationService.decide(AuthorizationRequest.forPermission(subject, permission, scope));
        List<AuthzGrantView> grants = decision.grants().stream()
                .map(grant -> new AuthzGrantView(
                        grant.source(),
                        grant.sourceReference(),
                        grant.scope().type().name(),
                        grant.scope().refId()))
                .toList();
        return new AuthzExplainView(
                userId,
                permission.code(),
                scopeType.name(),
                scopeRefId,
                decision.allowed(),
                decision.reasonCode(),
                grants);
    }

    private void requireExplainPermission(AuthenticatedUserPrincipal principal, ScopeRef scope) {
        if (!authorizationService
                .decide(AuthorizationRequest.forPermission(principal, PermissionCode.AUTH_EXPLAIN_READ, scope))
                .allowed()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前用户无权查看该授权解释");
        }
    }
}
