package com.irctc.rrs.common.events;

import com.irctc.rrs.common.model.PartitionKey;

import java.time.Instant;

public record BerthFreedEvent(
        PartitionKey partitionKey,
        String coachCode,
        int berthNumber,
        String cancelledPnr,
        String cancelledTicketId,
        Instant occurredAt
) {
}
