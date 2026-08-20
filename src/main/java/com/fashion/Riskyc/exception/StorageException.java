package com.fashion.Riskyc.exception;

/** Wraps any failure talking to MinIO (upload, presign, delete, stream). */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
