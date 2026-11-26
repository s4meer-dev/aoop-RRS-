package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class PaymentConflictException extends RrsException {
    public PaymentConflictException(String message) {
        super(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", message);
    }
}
