package com.irctc.rrs.booking.internal;

import com.irctc.rrs.common.exception.RrsException;
import com.irctc.rrs.common.exception.SystemBusyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RrsException.class)
    public ResponseEntity<Map<String, Object>> handleRrsException(RrsException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof SystemBusyException) {
            headers.add(HttpHeaders.RETRY_AFTER, "2");
        }
        return ResponseEntity.status(ex.getStatus())
                .headers(headers)
                .body(Map.of(
                        "errorCode", ex.getErrorCode(),
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "errorCode", "INVALID_REQUEST",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
