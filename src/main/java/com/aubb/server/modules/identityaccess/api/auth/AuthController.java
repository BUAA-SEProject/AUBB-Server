package com.aubb.server.modules.identityaccess.api.auth;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserView;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticationApplicationService;
import com.aubb.server.modules.identityaccess.application.auth.JwtTokenService;
import com.aubb.server.modules.identityaccess.application.auth.LoginResultView;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationApplicationService authenticationApplicationService;
    private final JwtTokenService jwtTokenService;

    @PostMapping("/login")
    public ResponseEntity<LoginResultView> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUserPrincipal principal =
                authenticationApplicationService.login(request.username(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jwtTokenService.issueToken(principal));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        authenticationApplicationService.logout(principal);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public AuthenticatedUserView currentUser(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return AuthenticatedUserView.from(principal);
    }

    public record LoginRequest(
            @NotBlank String username, @NotBlank String password) {}
}
