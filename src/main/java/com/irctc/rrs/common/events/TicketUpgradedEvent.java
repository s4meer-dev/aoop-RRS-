package com.irctc.rrs.common.events;

import com.irctc.rrs.common.model.TicketStatus;

import java.time.Instant;

public record TicketUpgradedEvent(
        String pnrNumber,
        String ticketId,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String details,
        Instant occurredAt
) {
}
