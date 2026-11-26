package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class ChartFinalizedException extends RrsException {
    public ChartFinalizedException(String message) {
        super(HttpStatus.CONFLICT, "CHART_ALREADY_PREPARED", message);
    }
}
