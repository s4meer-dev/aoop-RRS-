package com.irctc.rrs.common.events;

import java.time.Instant;

public record PaymentReconciledEvent(
        String paymentId,
        String pnrNumber,
        String idempotencyKey,
        boolean succeeded,
        Instant occurredAt
) {
}
