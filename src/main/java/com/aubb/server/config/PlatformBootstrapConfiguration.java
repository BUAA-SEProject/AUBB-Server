package com.aubb.server.config;

import com.aubb.server.modules.platformconfig.application.bootstrap.PlatformBootstrapApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformBootstrapProperties.class)
@Slf4j
public class PlatformBootstrapConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aubb.bootstrap", name = "enabled", havingValue = "true")
    ApplicationRunner platformBootstrapRunner(
            PlatformBootstrapProperties properties,
            PlatformBootstrapApplicationService platformBootstrapApplicationService) {
        return arguments -> {
            var result = platformBootstrapApplicationService.bootstrap(properties);
            log.info(
                    "Platform bootstrap completed schoolOrgUnitId={} adminUserId={} schoolCreated={} adminCreated={} schoolAdminRoleCreated={} academicProfileCreated={} platformConfigCreated={}",
                    result.schoolOrgUnitId(),
                    result.adminUserId(),
                    result.schoolCreated(),
                    result.adminCreated(),
                    result.schoolAdminRoleCreated(),
                    result.academicProfileCreated(),
                    result.platformConfigCreated());
        };
    }
}
