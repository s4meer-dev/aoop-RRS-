package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends RrsException {
    public RateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
    }
}
