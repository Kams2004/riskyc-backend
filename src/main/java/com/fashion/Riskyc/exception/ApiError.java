package com.fashion.Riskyc.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {
    public ApiError(int status, String error, String message) {
        this(Instant.now(), status, error, message, null);
    }

    public ApiError(int status, String error, String message, List<String> details) {
        this(Instant.now(), status, error, message, details);
    }
}
