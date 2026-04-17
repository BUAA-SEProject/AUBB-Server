package com.aubb.server.config;

import com.aubb.server.common.cache.CacheService;
import com.aubb.server.common.cache.NoOpCacheService;
import com.aubb.server.common.cache.RedisCacheService;
import com.aubb.server.common.ratelimit.NoOpRateLimitService;
import com.aubb.server.common.ratelimit.RateLimitAspect;
import com.aubb.server.common.ratelimit.RateLimitService;
import com.aubb.server.common.ratelimit.RedisRateLimitService;
import com.aubb.server.common.realtime.NoOpRealtimeCoordinationService;
import com.aubb.server.common.realtime.RealtimeCoordinationService;
import com.aubb.server.common.realtime.RedisRealtimeCoordinationService;
import com.aubb.server.common.redis.RedisAvailabilityTracker;
import com.aubb.server.common.redis.RedisEnhancementHealthIndicator;
import com.aubb.server.common.redis.RedisEnhancementMetrics;
import com.aubb.server.common.redis.RedisKeyFactory;
import io.lettuce.core.ClientOptions;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisEnhancementProperties.class)
public class RedisEnhancementConfiguration {

    @Bean
    RedisKeyFactory redisKeyFactory(RedisEnhancementProperties properties) {
        return new RedisKeyFactory(properties);
    }

    @Bean
    RedisEnhancementMetrics redisEnhancementMetrics(
            io.micrometer.core.instrument.MeterRegistry meterRegistry, RedisEnhancementProperties properties) {
        return new RedisEnhancementMetrics(meterRegistry, properties.isEnabled());
    }

    @Bean
    RedisAvailabilityTracker redisAvailabilityTracker(RedisEnhancementMetrics redisEnhancementMetrics) {
        return new RedisAvailabilityTracker(redisEnhancementMetrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "aubb.redis", name = "enabled", havingValue = "true")
    LettuceConnectionFactory redisConnectionFactory(RedisEnhancementProperties properties) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
        configuration.setDatabase(properties.getDatabase());
        if (StringUtils.hasText(properties.getPassword())) {
            configuration.setPassword(RedisPassword.of(properties.getPassword()));
        }
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(properties.getCommandTimeout())
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(ClientOptions.builder().autoReconnect(true).build())
                .build();
        return new LettuceConnectionFactory(configuration, clientConfiguration);
    }

    @Bean
    @ConditionalOnBean(LettuceConnectionFactory.class)
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    HealthIndicator redisEnhancementHealthIndicator(
            RedisEnhancementProperties properties,
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            RedisAvailabilityTracker redisAvailabilityTracker) {
        return new RedisEnhancementHealthIndicator(properties, redisTemplateProvider, redisAvailabilityTracker);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    CacheService redisBackedCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RedisKeyFactory redisKeyFactory,
            RedisEnhancementMetrics redisEnhancementMetrics,
            RedisAvailabilityTracker redisAvailabilityTracker) {
        return new RedisCacheService(
                redisTemplate, objectMapper, redisKeyFactory, redisEnhancementMetrics, redisAvailabilityTracker);
    }

    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    CacheService noOpCacheService() {
        return new NoOpCacheService();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(
            prefix = "aubb.redis.rate-limit",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    RateLimitService redisRateLimitService(
            StringRedisTemplate redisTemplate,
            RedisEnhancementProperties properties,
            RedisKeyFactory redisKeyFactory,
            RedisEnhancementMetrics redisEnhancementMetrics,
            RedisAvailabilityTracker redisAvailabilityTracker) {
        return new RedisRateLimitService(
                redisTemplate, properties, redisKeyFactory, redisEnhancementMetrics, redisAvailabilityTracker);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitService.class)
    RateLimitService noOpRateLimitService() {
        return new NoOpRateLimitService();
    }

    @Bean
    RateLimitAspect rateLimitAspect(RateLimitService rateLimitService, RedisKeyFactory redisKeyFactory) {
        return new RateLimitAspect(rateLimitService, redisKeyFactory);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(
            prefix = "aubb.redis.realtime",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    RealtimeCoordinationService redisRealtimeCoordinationService(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory redisKeyFactory,
            RedisAvailabilityTracker redisAvailabilityTracker) {
        return new RedisRealtimeCoordinationService(redisTemplate, redisKeyFactory, redisAvailabilityTracker);
    }

    @Bean
    @ConditionalOnMissingBean(RealtimeCoordinationService.class)
    RealtimeCoordinationService noOpRealtimeCoordinationService() {
        return new NoOpRealtimeCoordinationService();
    }
}
