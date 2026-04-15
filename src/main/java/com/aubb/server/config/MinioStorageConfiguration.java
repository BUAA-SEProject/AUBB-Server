package com.aubb.server.config;

import com.aubb.server.common.storage.MinioObjectStorageService;
import com.aubb.server.common.storage.MinioStorageHealthIndicator;
import com.aubb.server.common.storage.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MinioStorageProperties.class)
@Slf4j
public class MinioStorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aubb.storage.minio", name = "enabled", havingValue = "true")
    MinioClient minioClient(MinioStorageProperties properties) {
        validate(properties);
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnBean(MinioClient.class)
    ObjectStorageService objectStorageService(MinioClient minioClient, MinioStorageProperties properties) {
        return new MinioObjectStorageService(minioClient, properties.getBucket());
    }

    @Bean
    @ConditionalOnBean(MinioClient.class)
    ApplicationRunner minioBucketInitializer(MinioClient minioClient, MinioStorageProperties properties) {
        return arguments -> {
            if (!properties.isAutoCreateBucket()) {
                return;
            }
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("Created MinIO bucket bucket={}", properties.getBucket());
            }
        };
    }

    @Bean
    @ConditionalOnBean(MinioClient.class)
    HealthIndicator minioStorageHealthIndicator(MinioClient minioClient, MinioStorageProperties properties) {
        return new MinioStorageHealthIndicator(minioClient, properties.getBucket(), properties.getEndpoint());
    }

    private void validate(MinioStorageProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 aubb.storage.minio.endpoint");
        }
        if (!StringUtils.hasText(properties.getAccessKey())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 aubb.storage.minio.access-key");
        }
        if (!StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 aubb.storage.minio.secret-key");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("启用 MinIO 时必须配置 aubb.storage.minio.bucket");
        }
    }
}
