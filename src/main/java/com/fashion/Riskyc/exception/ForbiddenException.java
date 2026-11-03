package com.fashion.Riskyc.exception;

/** The caller is authenticated and holds the right permission in general, but this specific resource isn't theirs to touch (e.g. a different enterprise's product). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
