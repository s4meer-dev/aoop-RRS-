package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class SystemBusyException extends RrsException {
    public SystemBusyException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "TATKAL_BACKPRESSURE_SHED", message);
    }
}
