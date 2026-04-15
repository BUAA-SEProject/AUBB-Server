package com.aubb.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aubb.storage.minio")
@Getter
@Setter
public class MinioStorageProperties {

    private boolean enabled = false;
    private String endpoint = "http://localhost:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "aubb-assets";
    private boolean autoCreateBucket = false;
}
