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

class JwtSecurityPropertiesValidationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(JwtSecurityPropertiesTestConfiguration.class)
            .withPropertyValues("aubb.security.jwt.issuer=AUBB-Server", "aubb.security.jwt.ttl=PT2H");

    @Test
    void failsFastWhenJwtSecretIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("Could not bind properties to 'JwtSecurityProperties'")
                    .hasStackTraceContaining("aubb.security.jwt.secret");
        });
    }

    @Test
    void bindsJwtSettingsWhenSecretIsProvided() {
        contextRunner
                .withPropertyValues("aubb.security.jwt.secret=test-jwt-secret-for-aubb-server-0123456789")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtSecurityProperties properties = context.getBean(JwtSecurityProperties.class);
                    assertThat(properties.getIssuer()).isEqualTo("AUBB-Server");
                    assertThat(properties.getTtl()).hasToString("PT2H");
                    assertThat(properties.getSecret()).isEqualTo("test-jwt-secret-for-aubb-server-0123456789");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtSecurityProperties.class)
    static class JwtSecurityPropertiesTestConfiguration {

        @Bean(name = EnableConfigurationProperties.VALIDATOR_BEAN_NAME)
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        static LocalValidatorFactoryBean configurationPropertiesValidator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
