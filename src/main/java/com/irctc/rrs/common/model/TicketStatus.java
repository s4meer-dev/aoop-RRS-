package com.irctc.rrs.common.model;

/**
 * Ticket lifecycle states enforced via the State Pattern.
 */
public enum TicketStatus {
    INITIATED,
    PAYMENT_PENDING,
    WAITLISTED,
    RAC,
    CONFIRMED,
    CHARTED,
    CANCELLED,
    EXPIRED;

    public boolean isBoardable() {
        return this == CONFIRMED || this == RAC || this == CHARTED;
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == EXPIRED || this == CHARTED;
    }
}
