package com.fashion.Riskyc.service.impl;

import com.fashion.Riskyc.config.StorageProperties;
import com.fashion.Riskyc.exception.StorageException;
import com.fashion.Riskyc.service.MediaStream;
import com.fashion.Riskyc.service.S3MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to MinIO through the S3-compatible AWS SDK v2 client. Two things
 * keep media-heavy pages fast:
 *
 * <ol>
 *   <li><b>Presigned URL cache</b> — generating a presigned URL costs a
 *       round trip to MinIO (~hundreds of ms). Since a URL stays valid for
 *       {@code presignedUrlTtlMinutes}, there's no reason to regenerate it
 *       on every request: the first caller for a given key pays that cost,
 *       every caller afterwards (until the cache entry ages out, a few
 *       minutes before the real signature expires) gets it back in
 *       microseconds from a {@link ConcurrentHashMap}.</li>
 *   <li><b>Range-aware streaming</b> — {@link #openStream} never buffers
 *       the whole object in memory; it opens a direct stream from MinIO,
 *       optionally scoped to the byte range the client asked for, so large
 *       product videos can be seeked without downloading the entire file.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3MediaServiceImpl implements S3MediaService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    private record CachedUrl(String url, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final Map<String, CachedUrl> presignedUrlCache = new ConcurrentHashMap<>();

    @Override
    public String upload(MultipartFile file, String folder) {
        String key = "%s/%s%s".formatted(folder, UUID.randomUUID(), extractExtension(file.getOriginalFilename()));
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            log.debug("Uploaded '{}' ({} bytes) to key '{}'", file.getOriginalFilename(), file.getSize(), key);
            return key;
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file", e);
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to storage", e);
        }
    }

    @Override
    public String getPresignedUrl(String storageKey) {
        CachedUrl cached = presignedUrlCache.get(storageKey);
        if (cached != null && cached.isValid()) {
            return cached.url();
        }

        String url = generatePresignedUrl(storageKey);
        Instant expiresAt = Instant.now().plus(storageProperties.getPresignedCacheTtlMinutes(), ChronoUnit.MINUTES);
        presignedUrlCache.put(storageKey, new CachedUrl(url, expiresAt));
        return url;
    }

    private String generatePresignedUrl(String storageKey) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(storageProperties.getPresignedUrlTtlMinutes()))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(storageProperties.getBucket())
                            .key(storageKey)
                            .build())
                    .build();
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception e) {
            throw new StorageException("Failed to presign URL for key '" + storageKey + "'", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(storageKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete key '" + storageKey + "'", e);
        } finally {
            // Always drop the cache entry, even if the delete call itself
            // failed after actually removing the object, so we never keep
            // serving a URL to bytes that might no longer exist.
            presignedUrlCache.remove(storageKey);
        }
    }

    @Override
    public MediaStream openStream(String storageKey, String rangeHeader) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(storageKey)
                    .build());
            long totalSize = head.contentLength();
            String contentType = head.contentType();

            long start = 0;
            long end = totalSize - 1;
            boolean partial = false;

            ByteRange range = parseRange(rangeHeader, totalSize);
            if (range != null) {
                start = range.start();
                end = range.end();
                partial = true;
            }

            GetObjectRequest.Builder getRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(storageKey);
            if (partial) {
                getRequest.range("bytes=%d-%d".formatted(start, end));
            }

            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getRequest.build());
            return new MediaStream(stream, contentType, totalSize, partial, start, end);
        } catch (NoSuchKeyException e) {
            throw new StorageException("Object not found in storage: " + storageKey, e);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to open stream for key '" + storageKey + "'", e);
        }
    }

    private record ByteRange(long start, long end) {
    }

    /** Parses an HTTP {@code Range: bytes=start-end} header, tolerating open-ended forms like {@code bytes=200-}. */
    private ByteRange parseRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || totalSize <= 0) {
            return null;
        }
        String spec = rangeHeader.substring("bytes=".length()).split(",")[0].trim();
        String[] parts = spec.split("-", -1);
        try {
            long start = parts[0].isBlank() ? 0 : Long.parseLong(parts[0]);
            long end = (parts.length > 1 && !parts[1].isBlank()) ? Long.parseLong(parts[1]) : totalSize - 1;
            end = Math.min(end, totalSize - 1);
            if (start < 0 || start > end) {
                return null;
            }
            return new ByteRange(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
