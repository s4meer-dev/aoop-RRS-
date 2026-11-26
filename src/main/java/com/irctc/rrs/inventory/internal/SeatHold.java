package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.inventory.InventoryService.HoldAllocationType;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class SeatHold {

    private final String holdId;
    private final PartitionKey partitionKey;
    private final HoldAllocationType allocationType;
    private final String coachCode;
    private final Integer berthNumber;
    private final String berthType;
    private final int tentativeQueueNumber;
    private final Instant expiresAt;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public SeatHold(
            String holdId,
            PartitionKey partitionKey,
            HoldAllocationType allocationType,
            String coachCode,
            Integer berthNumber,
            String berthType,
            int tentativeQueueNumber,
            Instant expiresAt
    ) {
        this.holdId = holdId;
        this.partitionKey = partitionKey;
        this.allocationType = allocationType;
        this.coachCode = coachCode;
        this.berthNumber = berthNumber;
        this.berthType = berthType;
        this.tentativeQueueNumber = tentativeQueueNumber;
        this.expiresAt = expiresAt;
    }

    public String getHoldId() {
        return holdId;
    }

    public PartitionKey getPartitionKey() {
        return partitionKey;
    }

    public HoldAllocationType getAllocationType() {
        return allocationType;
    }

    public String getCoachCode() {
        return coachCode;
    }

    public Integer getBerthNumber() {
        return berthNumber;
    }

    public String getBerthType() {
        return berthType;
    }

    public int getTentativeQueueNumber() {
        return tentativeQueueNumber;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active.get();
    }

    public boolean deactivate() {
        return active.compareAndSet(true, false);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
