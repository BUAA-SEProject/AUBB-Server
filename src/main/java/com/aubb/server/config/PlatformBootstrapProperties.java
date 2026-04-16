package com.aubb.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("aubb.bootstrap")
@Validated
@Getter
@Setter
public class PlatformBootstrapProperties {

    private boolean enabled = false;

    @Valid
    private School school = new School();

    @Valid
    private Admin admin = new Admin();

    @Valid
    private PlatformConfig platformConfig = new PlatformConfig();

    @AssertTrue(
            message =
                    "启用 aubb.bootstrap 时必须配置 school.code、school.name、admin.username、admin.display-name、admin.email、admin.password、admin.academic-id")
    public boolean isBootstrapConfigurationComplete() {
        if (!enabled) {
            return true;
        }
        return hasText(school.getCode())
                && hasText(school.getName())
                && hasText(admin.getUsername())
                && hasText(admin.getDisplayName())
                && hasText(admin.getEmail())
                && hasText(admin.getPassword())
                && hasText(admin.getAcademicId());
    }

    @AssertTrue(
            message =
                    "启用 aubb.bootstrap 时 platform-config.platform-name/platform-short-name/default-home-path/theme-key 必须可解析为非空")
    public boolean isResolvedPlatformConfigurationValid() {
        if (!enabled) {
            return true;
        }
        return hasText(resolvedPlatformName())
                && hasText(resolvedPlatformShortName())
                && hasText(resolvedDefaultHomePath())
                && hasText(resolvedThemeKey());
    }

    public String resolvedPlatformName() {
        return hasText(platformConfig.getPlatformName())
                ? platformConfig.getPlatformName().trim()
                : school.getName().trim();
    }

    public String resolvedPlatformShortName() {
        return hasText(platformConfig.getPlatformShortName())
                ? platformConfig.getPlatformShortName().trim()
                : school.getCode().trim().toUpperCase();
    }

    public String resolvedDefaultHomePath() {
        return platformConfig.getDefaultHomePath().trim();
    }

    public String resolvedThemeKey() {
        return platformConfig.getThemeKey().trim();
    }

    public String resolvedAdminRealName() {
        return hasText(admin.getRealName())
                ? admin.getRealName().trim()
                : admin.getDisplayName().trim();
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    @Getter
    @Setter
    public static class School {

        private String code;

        private String name;

        private int sortOrder = 1;
    }

    @Getter
    @Setter
    public static class Admin {

        private String username;

        private String displayName;

        private String email;

        private String password;

        private String academicId;

        private String realName;

        private String phone;
    }

    @Getter
    @Setter
    public static class PlatformConfig {

        private String platformName;

        private String platformShortName;

        private String logoUrl;

        private String footerText;

        private String defaultHomePath = "/admin";

        private String themeKey = "aubb-light";

        private String loginNotice;

        private Map<String, Object> moduleFlags = new LinkedHashMap<>();
    }
}
