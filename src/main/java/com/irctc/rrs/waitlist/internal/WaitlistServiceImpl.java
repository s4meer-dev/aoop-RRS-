package com.irctc.rrs.waitlist.internal;

import com.irctc.rrs.common.concurrency.PartitionLockManager;
import com.irctc.rrs.common.id.IdGenerator;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.waitlist.WaitlistService;
import com.irctc.rrs.waitlist.internal.ContiguousQueue.MutableEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class WaitlistServiceImpl implements WaitlistService {

    private static final int MAX_AUDIT_SIZE = 200;

    private final PartitionLockManager lockManager;
    private final TimeProvider timeProvider;
    private final IdGenerator idGenerator;

    private final Map<PartitionKey, ContiguousQueue> queues = new ConcurrentHashMap<>();
    private final Deque<PromotionAuditEntry> auditLog = new ConcurrentLinkedDeque<>();

    public WaitlistServiceImpl(
            PartitionLockManager lockManager,
            TimeProvider timeProvider,
            IdGenerator idGenerator
    ) {
        this.lockManager = lockManager;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
    }

    @Override
    public WaitlistEntrySnapshot enqueueRac(
            PartitionKey key,
            String ticketId,
            String pnrNumber,
            String passengerName,
            String allocatedCoach,
            Integer allocatedBerth
    ) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            return q.addRac(ticketId, pnrNumber, passengerName, allocatedCoach, allocatedBerth, timeProvider.now());
        });
    }

    @Override
    public WaitlistEntrySnapshot enqueueWaitlist(
            PartitionKey key,
            String ticketId,
            String pnrNumber,
            String passengerName
    ) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            return q.addWaitlist(ticketId, pnrNumber, passengerName, timeProvider.now());
        });
    }

    @Override
    public CascadeUpgradeResult handleConfirmedCancellation(PartitionKey key, String freedCoach, Integer freedBerth) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            List<ShiftedEntry> shifts = new ArrayList<>();
            Instant now = timeProvider.now();

            MutableEntry racWinner = q.popFirstRac(shifts);
            if (racWinner == null) {
                return new CascadeUpgradeResult(null, freedCoach, freedBerth, null, null, shifts);
            }

            String vacatedRacSlotRef = (racWinner.getAllocatedCoach() != null && racWinner.getAllocatedBerth() != null)
                    ? racWinner.getAllocatedCoach() + "-" + racWinner.getAllocatedBerth()
                    : null;

            recordAudit(key, racWinner.getTicketId(), racWinner.getPnrNumber(),
                    "RAC #1 promoted to CONFIRMED (" + freedCoach + "-" + freedBerth + ")", now);

            MutableEntry wlWinner = q.popFirstWaitlist(shifts);
            WaitlistEntrySnapshot promotedToRacSnap = null;
            if (wlWinner != null) {
                q.appendPromotedWaitlistToRac(wlWinner, racWinner.getAllocatedCoach(), racWinner.getAllocatedBerth());
                promotedToRacSnap = wlWinner.toSnapshot();
                recordAudit(key, wlWinner.getTicketId(), wlWinner.getPnrNumber(),
                        "WL #1 promoted to RAC #" + wlWinner.getContiguousPosition(), now);
            }

            return new CascadeUpgradeResult(
                    racWinner.toSnapshot(),
                    freedCoach,
                    freedBerth,
                    promotedToRacSnap,
                    vacatedRacSlotRef,
                    shifts
            );
        });
    }

    @Override
    public CascadeUpgradeResult handleRacCancellation(PartitionKey key, String cancelledTicketId) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            List<ShiftedEntry> shifts = new ArrayList<>();
            Instant now = timeProvider.now();

            MutableEntry removed = q.removeRacByTicketId(cancelledTicketId, shifts);
            String vacatedSlotRef = (removed != null && removed.getAllocatedCoach() != null && removed.getAllocatedBerth() != null)
                    ? removed.getAllocatedCoach() + "-" + removed.getAllocatedBerth()
                    : null;

            MutableEntry wlWinner = q.popFirstWaitlist(shifts);
            WaitlistEntrySnapshot promotedToRacSnap = null;
            if (wlWinner != null) {
                String slotCoach = removed != null ? removed.getAllocatedCoach() : null;
                Integer slotBerth = removed != null ? removed.getAllocatedBerth() : null;
                q.appendPromotedWaitlistToRac(wlWinner, slotCoach, slotBerth);
                promotedToRacSnap = wlWinner.toSnapshot();
                recordAudit(key, wlWinner.getTicketId(), wlWinner.getPnrNumber(),
                        "WL #1 promoted to RAC #" + wlWinner.getContiguousPosition() + " after RAC cancellation", now);
            }

            return new CascadeUpgradeResult(null, null, null, promotedToRacSnap, vacatedSlotRef, shifts);
        });
    }

    @Override
    public CascadeUpgradeResult handleWaitlistCancellation(PartitionKey key, String cancelledTicketId) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            List<ShiftedEntry> shifts = new ArrayList<>();
            q.removeWaitlistByTicketId(cancelledTicketId, shifts);
            return new CascadeUpgradeResult(null, null, null, null, null, shifts);
        });
    }

    @Override
    public List<WaitlistEntrySnapshot> drainWaitlistOnCharting(PartitionKey key) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            return q.drainWaitlist();
        });
    }

    @Override
    public QueueSnapshot getQueueSnapshot(PartitionKey key) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.computeIfAbsent(key, ignored -> new ContiguousQueue());
            return new QueueSnapshot(key, q.snapshotRac(), q.snapshotWaitlist(), q.isStrictlyContiguous());
        });
    }

    @Override
    public List<PromotionAuditEntry> getRecentAuditLog(int limit) {
        return auditLog.stream().limit(Math.max(1, limit)).toList();
    }

    @Override
    public boolean verifyContiguousInvariant(PartitionKey key) {
        return lockManager.executeWithLock(key, () -> {
            ContiguousQueue q = queues.get(key);
            return q == null || q.isStrictlyContiguous();
        });
    }

    private void recordAudit(PartitionKey key, String ticketId, String pnrNumber, String desc, Instant now) {
        auditLog.addFirst(new PromotionAuditEntry(
                idGenerator.nextId("PROMO"), key, ticketId, pnrNumber, desc, now
        ));
        while (auditLog.size() > MAX_AUDIT_SIZE) {
            auditLog.pollLast();
        }
    }
}
