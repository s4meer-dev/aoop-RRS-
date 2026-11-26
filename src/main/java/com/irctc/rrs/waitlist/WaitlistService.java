package com.irctc.rrs.waitlist;

import com.irctc.rrs.common.model.PartitionKey;

import java.time.Instant;
import java.util.List;

/**
 * Public API of the Waitlist Bounded Context (Unit 7 + Slide 6 Gap-Free Cascades).
 */
public interface WaitlistService {

    enum QueueType {
        RAC,
        WL
    }

    WaitlistEntrySnapshot enqueueRac(
            PartitionKey key,
            String ticketId,
            String pnrNumber,
            String passengerName,
            String allocatedCoach,
            Integer allocatedBerth
    );

    WaitlistEntrySnapshot enqueueWaitlist(
            PartitionKey key,
            String ticketId,
            String pnrNumber,
            String passengerName
    );

    CascadeUpgradeResult handleConfirmedCancellation(PartitionKey key, String freedCoach, Integer freedBerth);

    CascadeUpgradeResult handleRacCancellation(PartitionKey key, String cancelledTicketId);

    CascadeUpgradeResult handleWaitlistCancellation(PartitionKey key, String cancelledTicketId);

    List<WaitlistEntrySnapshot> drainWaitlistOnCharting(PartitionKey key);

    QueueSnapshot getQueueSnapshot(PartitionKey key);

    List<PromotionAuditEntry> getRecentAuditLog(int limit);

    boolean verifyContiguousInvariant(PartitionKey key);

    record WaitlistEntrySnapshot(
            String ticketId,
            String pnrNumber,
            String passengerName,
            QueueType queueType,
            int contiguousPosition,
            String allocatedCoach,
            Integer allocatedBerth,
            Instant enqueuedAt
    ) {}

    record ShiftedEntry(
            String ticketId,
            String pnrNumber,
            QueueType queueType,
            int oldPosition,
            int newPosition
    ) {}

    record CascadeUpgradeResult(
            WaitlistEntrySnapshot promotedToConfirmed,
            String freedCoachForRacWinner,
            Integer freedBerthForRacWinner,
            WaitlistEntrySnapshot promotedToRac,
            String vacatedRacSlotRef,
            List<ShiftedEntry> shiftedEntries
    ) {}

    record QueueSnapshot(
            PartitionKey partitionKey,
            List<WaitlistEntrySnapshot> racQueue,
            List<WaitlistEntrySnapshot> wlQueue,
            boolean contiguousInvariantValid
    ) {}

    record PromotionAuditEntry(
            String id,
            PartitionKey partitionKey,
            String ticketId,
            String pnrNumber,
            String transitionDescription,
            Instant occurredAt
    ) {}
}
