package com.aubb.server.api.admin.platformconfig;

import com.aubb.server.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.application.platformconfig.PlatformConfigApplicationService;
import com.aubb.server.application.platformconfig.PlatformConfigView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/platform-config")
@RequiredArgsConstructor
public class PlatformConfigAdminController {

    private final PlatformConfigApplicationService platformConfigApplicationService;

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('SCHOOL_ADMIN')")
    public PlatformConfigView current() {
        return platformConfigApplicationService.getCurrent();
    }

    @PutMapping("/current")
    @PreAuthorize("hasAuthority('SCHOOL_ADMIN')")
    public PlatformConfigView updateCurrent(
            @Valid @RequestBody PlatformConfigRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return platformConfigApplicationService.upsertCurrent(
                request.platformName(),
                request.platformShortName(),
                request.logoUrl(),
                request.footerText(),
                request.defaultHomePath(),
                request.themeKey(),
                request.loginNotice(),
                request.moduleFlags(),
                principal.getUserId());
    }

    public record PlatformConfigRequest(
            @NotBlank String platformName,
            @NotBlank String platformShortName,
            String logoUrl,
            String footerText,
            @NotBlank String defaultHomePath,
            @NotBlank String themeKey,
            String loginNotice,
            Map<String, Object> moduleFlags) {}
}
