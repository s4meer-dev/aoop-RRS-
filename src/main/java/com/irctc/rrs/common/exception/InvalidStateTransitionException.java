package com.irctc.rrs.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends RrsException {
    public InvalidStateTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_TICKET_STATE_TRANSITION", message);
    }
}
