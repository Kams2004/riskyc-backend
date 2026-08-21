package com.fashion.Riskyc.exception;

/** The request can't be applied because of the resource's current state (e.g. someone else already claimed it). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
