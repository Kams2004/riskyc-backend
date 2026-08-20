package com.fashion.Riskyc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code app.storage.*} properties used to talk to the MinIO
 * (S3-compatible) object storage server.
 */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

    private String endpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean pathStyleAccess = true;
    private long presignedUrlTtlMinutes = 60;
    private long presignedCacheTtlMinutes = 55;
    private String publicBaseUrl;
}
