package com.aubb.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PlatformBootstrapPropertiesValidationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PlatformBootstrapPropertiesTestConfiguration.class);

    @Test
    void allowsBlankBootstrapSettingsWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            PlatformBootstrapProperties properties = context.getBean(PlatformBootstrapProperties.class);
            assertThat(properties.isEnabled()).isFalse();
        });
    }

    @Test
    void failsFastWhenBootstrapEnabledButRequiredSettingsAreMissing() {
        contextRunner.withPropertyValues("aubb.bootstrap.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("Could not bind properties to 'PlatformBootstrapProperties'")
                    .hasStackTraceContaining("aubb.bootstrap");
        });
    }

    @Test
    void bindsBootstrapSettingsWhenRequiredPropertiesArePresent() {
        contextRunner
                .withPropertyValues(
                        "aubb.bootstrap.enabled=true",
                        "aubb.bootstrap.school.code=SCH-1",
                        "aubb.bootstrap.school.name=AUBB School",
                        "aubb.bootstrap.admin.username=school-admin",
                        "aubb.bootstrap.admin.display-name=School Admin",
                        "aubb.bootstrap.admin.email=school-admin@example.com",
                        "aubb.bootstrap.admin.password=Password123",
                        "aubb.bootstrap.admin.academic-id=AUBB-ADMIN-001")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PlatformBootstrapProperties properties = context.getBean(PlatformBootstrapProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.resolvedPlatformName()).isEqualTo("AUBB School");
                    assertThat(properties.resolvedPlatformShortName()).isEqualTo("SCH-1");
                    assertThat(properties.resolvedDefaultHomePath()).isEqualTo("/admin");
                    assertThat(properties.resolvedThemeKey()).isEqualTo("aubb-light");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlatformBootstrapProperties.class)
    static class PlatformBootstrapPropertiesTestConfiguration {

        @Bean(name = EnableConfigurationProperties.VALIDATOR_BEAN_NAME)
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        static LocalValidatorFactoryBean configurationPropertiesValidator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
