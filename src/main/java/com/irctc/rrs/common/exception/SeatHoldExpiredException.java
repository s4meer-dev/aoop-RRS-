package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class SeatHoldExpiredException extends RrsException {
    public SeatHoldExpiredException(String message) {
        super(HttpStatus.CONFLICT, "SEAT_HOLD_EXPIRED", message);
    }
}
