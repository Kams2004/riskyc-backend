package com.fashion.Riskyc.exception;

/** 401 — the request needs a signed-in customer (no token, or it expired). The apps sign the customer out on it. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
