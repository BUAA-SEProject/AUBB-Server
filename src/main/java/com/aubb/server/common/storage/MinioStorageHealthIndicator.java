package com.aubb.server.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

@RequiredArgsConstructor
public class MinioStorageHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final String bucket;
    private final String endpoint;

    @Override
    public Health health() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (bucketExists) {
                return Health.up()
                        .withDetail("bucket", bucket)
                        .withDetail("endpoint", endpoint)
                        .build();
            }
            return Health.down()
                    .withDetail("bucket", bucket)
                    .withDetail("endpoint", endpoint)
                    .withDetail("reason", "bucket_missing")
                    .build();
        } catch (Exception exception) {
            return Health.down(exception)
                    .withDetail("bucket", bucket)
                    .withDetail("endpoint", endpoint)
                    .build();
        }
    }
}
