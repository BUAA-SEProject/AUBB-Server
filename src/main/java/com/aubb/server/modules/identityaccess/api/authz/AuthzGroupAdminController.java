package com.aubb.server.modules.identityaccess.api.authz;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.AuthzExplainApplicationService;
import com.aubb.server.modules.identityaccess.application.authz.AuthzGroupApplicationService;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzExplainView;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzGroupMemberView;
import com.aubb.server.modules.identityaccess.application.authz.view.AuthzGroupView;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AuthzGroupAdminController {

    private final AuthzGroupApplicationService authzGroupApplicationService;
    private final AuthzExplainApplicationService authzExplainApplicationService;

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AuthzGroupView createGroup(
            @Valid @RequestBody CreateAuthzGroupRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return authzGroupApplicationService.createGroup(
                request.templateCode(), request.scopeType(), request.scopeRefId(), request.displayName(), principal);
    }

    @PostMapping("/groups/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AuthzGroupMemberView addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddAuthzGroupMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return authzGroupApplicationService.addMember(groupId, request.userId(), request.expiresAt(), principal);
    }

    @GetMapping("/explain")
    @PreAuthorize("isAuthenticated()")
    public AuthzExplainView explain(
            @RequestParam Long userId,
            @RequestParam PermissionCode permission,
            @RequestParam AuthorizationScopeType scopeType,
            @RequestParam Long scopeRefId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return authzExplainApplicationService.explain(userId, permission, scopeType, scopeRefId, principal);
    }

    public record CreateAuthzGroupRequest(
            @NotBlank String templateCode,
            @NotNull AuthorizationScopeType scopeType,
            @NotNull Long scopeRefId,
            @NotBlank @Size(max = 128) String displayName) {}

    public record AddAuthzGroupMemberRequest(@NotNull Long userId, OffsetDateTime expiresAt) {}
}
