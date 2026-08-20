package com.fashion.Riskyc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Wires up the AWS SDK v2 {@link S3Client} / {@link S3Presigner} beans
 * against a MinIO endpoint. MinIO speaks the S3 API, so the standard AWS
 * SDK works against it as long as path-style addressing is forced and a
 * static access/secret key pair (rather than IAM roles) is supplied.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final StorageProperties storageProperties;

    private AwsBasicCredentials credentials() {
        return AwsBasicCredentials.create(storageProperties.getAccessKey(), storageProperties.getSecretKey());
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(storageProperties.getEndpoint()))
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storageProperties.isPathStyleAccess())
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(storageProperties.effectivePublicEndpoint()))
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storageProperties.isPathStyleAccess())
                        .build())
                .build();
    }

    /**
     * Creates the media bucket on startup if it doesn't already exist, so a
     * freshly provisioned MinIO instance works out of the box.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketExists() {
        String bucket = storageProperties.getBucket();
        S3Client client = s3Client();
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("MinIO bucket '{}' already exists", bucket);
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created MinIO bucket '{}'", bucket);
        } catch (Exception e) {
            log.warn("Could not verify/create MinIO bucket '{}' at startup: {}", bucket, e.getMessage());
        }
    }
}
