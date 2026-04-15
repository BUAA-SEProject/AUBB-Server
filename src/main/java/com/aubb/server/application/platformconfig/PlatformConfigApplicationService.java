package com.aubb.server.application.platformconfig;

import com.aubb.server.application.audit.AuditLogApplicationService;
import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.domain.audit.AuditAction;
import com.aubb.server.domain.audit.AuditResult;
import com.aubb.server.infrastructure.platformconfig.PlatformConfigEntity;
import com.aubb.server.infrastructure.platformconfig.PlatformConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformConfigApplicationService {

    private final PlatformConfigMapper platformConfigMapper;
    private final AuditLogApplicationService auditLogApplicationService;

    @Transactional(readOnly = true)
    public PlatformConfigView getCurrent() {
        PlatformConfigEntity entity = currentEntity();
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "PLATFORM_CONFIG_NOT_FOUND", "当前没有平台配置");
        }
        return toView(entity);
    }

    @Transactional
    public PlatformConfigView upsertCurrent(
            String platformName,
            String platformShortName,
            String logoUrl,
            String footerText,
            String defaultHomePath,
            String themeKey,
            String loginNotice,
            Map<String, Object> moduleFlags,
            Long actorUserId) {
        PlatformConfigEntity entity = currentEntity();
        if (entity == null) {
            entity = new PlatformConfigEntity();
            applyMutableFields(
                    entity,
                    platformName,
                    platformShortName,
                    logoUrl,
                    footerText,
                    defaultHomePath,
                    themeKey,
                    loginNotice,
                    moduleFlags);
            entity.setUpdatedByUserId(actorUserId);
            platformConfigMapper.insert(entity);
        } else {
            applyMutableFields(
                    entity,
                    platformName,
                    platformShortName,
                    logoUrl,
                    footerText,
                    defaultHomePath,
                    themeKey,
                    loginNotice,
                    moduleFlags);
            entity.setUpdatedByUserId(actorUserId);
            platformConfigMapper.updateById(entity);
        }
        auditLogApplicationService.record(
                actorUserId,
                AuditAction.PLATFORM_CONFIG_UPDATED,
                "PLATFORM_CONFIG",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("platformName", platformName, "themeKey", themeKey));
        return toView(entity);
    }

    private PlatformConfigEntity currentEntity() {
        return platformConfigMapper.selectOne(Wrappers.<PlatformConfigEntity>lambdaQuery()
                .orderByAsc(PlatformConfigEntity::getId)
                .last("LIMIT 1"));
    }

    private void applyMutableFields(
            PlatformConfigEntity entity,
            String platformName,
            String platformShortName,
            String logoUrl,
            String footerText,
            String defaultHomePath,
            String themeKey,
            String loginNotice,
            Map<String, Object> moduleFlags) {
        entity.setPlatformName(platformName);
        entity.setPlatformShortName(platformShortName);
        entity.setLogoUrl(logoUrl);
        entity.setFooterText(footerText);
        entity.setDefaultHomePath(defaultHomePath);
        entity.setThemeKey(themeKey);
        entity.setLoginNotice(loginNotice);
        entity.setModuleFlags(moduleFlags == null ? Map.of() : moduleFlags);
    }

    private PlatformConfigView toView(PlatformConfigEntity entity) {
        return new PlatformConfigView(
                entity.getId(),
                entity.getPlatformName(),
                entity.getPlatformShortName(),
                entity.getLogoUrl(),
                entity.getFooterText(),
                entity.getDefaultHomePath(),
                entity.getThemeKey(),
                entity.getLoginNotice(),
                entity.getModuleFlags(),
                entity.getUpdatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
