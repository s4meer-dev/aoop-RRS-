package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class QuotaEligibilityException extends RrsException {
    public QuotaEligibilityException(String message) {
        super(HttpStatus.BAD_REQUEST, "QUOTA_ELIGIBILITY_FAILED", message);
    }
}
