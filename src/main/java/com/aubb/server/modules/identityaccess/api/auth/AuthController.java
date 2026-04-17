package com.aubb.server.modules.identityaccess.api.auth;

import com.aubb.server.common.ratelimit.RateLimited;
import com.aubb.server.modules.identityaccess.application.auth.AuthSessionApplicationService;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticationApplicationService;
import com.aubb.server.modules.identityaccess.application.auth.LoginResultView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationApplicationService authenticationApplicationService;
    private final AuthSessionApplicationService authSessionApplicationService;

    @PostMapping("/login")
    @Operation(summary = "账号登录并签发 access token 与 refresh token")
    @RateLimited(policy = "login", subject = "#request.username")
    public ResponseEntity<LoginResultView> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUserPrincipal principal =
                authenticationApplicationService.login(request.username(), request.password());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authSessionApplicationService.createSession(principal));
    }

    @PostMapping("/logout")
    @Operation(summary = "撤销当前 Bearer 会话并退出登录")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        authSessionApplicationService.logoutCurrentSession(principal);
    }

    @PostMapping("/refresh")
    @Operation(summary = "使用 refresh token 刷新 access token")
    @RateLimited(policy = "refresh")
    public ResponseEntity<LoginResultView> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authSessionApplicationService.refresh(request.refreshToken()));
    }

    @PostMapping("/revoke")
    @Operation(summary = "按 refresh token 主动撤销当前登录会话")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@Valid @RequestBody RevokeRequest request) {
        authSessionApplicationService.revokeByRefreshToken(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "读取当前登录用户快照")
    public AuthenticatedUserView currentUser(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return AuthenticatedUserView.from(principal);
    }

    public record LoginRequest(
            @Schema(description = "用户名") @NotBlank String username,
            @Schema(description = "密码") @NotBlank String password) {}

    public record RefreshRequest(
            @Schema(description = "refresh token") @NotBlank String refreshToken) {}

    public record RevokeRequest(
            @Schema(description = "refresh token") @NotBlank String refreshToken) {}
}
