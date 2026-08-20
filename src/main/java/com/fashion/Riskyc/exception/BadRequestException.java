package com.fashion.Riskyc.exception;

/** A client-supplied request is invalid in a way bean validation can't express. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
