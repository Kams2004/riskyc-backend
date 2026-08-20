package com.fashion.Riskyc.service;

import java.io.InputStream;

/**
 * Carries an open stream over an object's bytes back to the controller,
 * along with everything needed to build either a plain {@code 200 OK} or a
 * {@code 206 Partial Content} response (HTTP Range support for video
 * seeking) without the controller having to know anything about MinIO.
 */
public record MediaStream(
        InputStream inputStream,
        String contentType,
        long totalSize,
        boolean partial,
        long rangeStart,
        long rangeEnd
) {
    /** Number of bytes actually being returned in this response. */
    public long contentLength() {
        return rangeEnd - rangeStart + 1;
    }
}
