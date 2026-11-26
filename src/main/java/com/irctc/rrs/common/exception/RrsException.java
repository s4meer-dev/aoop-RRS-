package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception carrying structured HTTP status & error code across bounded contexts.
 */
public abstract class RrsException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected RrsException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
