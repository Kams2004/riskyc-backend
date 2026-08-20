package com.fashion.Riskyc.service;

import org.springframework.web.multipart.MultipartFile;

public interface S3MediaService {

    /**
     * Uploads a file into {@code folder} under a random key and returns the
     * MinIO object key (not a URL — URLs are resolved on demand and cached).
     */
    String upload(MultipartFile file, String folder);

    /**
     * Returns a presigned GET URL for {@code storageKey}, served from an
     * in-memory cache when a still-valid one was generated recently instead
     * of round-tripping to MinIO + re-signing on every call.
     */
    String getPresignedUrl(String storageKey);

    /** Deletes the object from storage and evicts any cached presigned URL for it. */
    void delete(String storageKey);

    /**
     * Opens a stream over the object's bytes, honoring an HTTP
     * {@code Range: bytes=X-Y} header when present so video playback can
     * seek without downloading the whole file.
     */
    MediaStream openStream(String storageKey, String rangeHeader);
}
