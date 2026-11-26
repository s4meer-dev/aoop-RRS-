package com.irctc.rrs.common.events;

import com.irctc.rrs.common.model.Money;

import java.time.Instant;

public record RefundTriggeredEvent(
        String refundId,
        String paymentId,
        String pnrNumber,
        Money amount,
        String reason,
        Instant occurredAt
) {
}
