package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.PartitionKey;

/**
 * Encapsulates seat, RAC, and waitlist quotas for one (Train, Date, Class, Quota) partition.
 */
public class QuotaPool {

    private final PartitionKey partitionKey;
    private final int totalCnfSlots;
    private final int totalRacSlots;
    private final int maxWaitlistLimit;

    private int confirmedCount;
    private int heldCnfCount;
    private int racCount;
    private int heldRacCount;
    private int waitlistCount;
    private int heldWaitlistCount;

    public QuotaPool(PartitionKey partitionKey, int totalCnfSlots, int totalRacSlots, int maxWaitlistLimit) {
        this.partitionKey = partitionKey;
        this.totalCnfSlots = totalCnfSlots;
        this.totalRacSlots = totalRacSlots;
        this.maxWaitlistLimit = maxWaitlistLimit;
    }

    public PartitionKey getPartitionKey() {
        return partitionKey;
    }

    public int getTotalCnfSlots() {
        return totalCnfSlots;
    }

    public int getAvailableCnfSlots() {
        return Math.max(0, totalCnfSlots - confirmedCount - heldCnfCount);
    }

    public int getTotalRacSlots() {
        return totalRacSlots;
    }

    public int getAvailableRacSlots() {
        return Math.max(0, totalRacSlots - racCount - heldRacCount);
    }

    public int getMaxWaitlistLimit() {
        return maxWaitlistLimit;
    }

    public int getCurrentWaitlistCount() {
        return waitlistCount;
    }

    public int getAvailableWaitlistSlots() {
        return Math.max(0, maxWaitlistLimit - waitlistCount - heldWaitlistCount);
    }

    public int getConfirmedCount() {
        return confirmedCount;
    }

    public int getRacCount() {
        return racCount;
    }

    public boolean holdCnf() {
        if (getAvailableCnfSlots() <= 0) {
            return false;
        }
        heldCnfCount++;
        return true;
    }

    public boolean holdRac() {
        if (getAvailableRacSlots() <= 0) {
            return false;
        }
        heldRacCount++;
        return true;
    }

    public int holdWaitlist() {
        if (getAvailableWaitlistSlots() <= 0) {
            return -1;
        }
        heldWaitlistCount++;
        return waitlistCount + heldWaitlistCount;
    }

    public void commitCnfHold() {
        if (heldCnfCount > 0) {
            heldCnfCount--;
        }
        confirmedCount++;
    }

    public void commitRacHold() {
        if (heldRacCount > 0) {
            heldRacCount--;
        }
        racCount++;
    }

    public void commitWaitlistHold() {
        if (heldWaitlistCount > 0) {
            heldWaitlistCount--;
        }
        waitlistCount++;
    }

    public void releaseCnfHold() {
        if (heldCnfCount > 0) {
            heldCnfCount--;
        }
    }

    public void releaseRacHold() {
        if (heldRacCount > 0) {
            heldRacCount--;
        }
    }

    public void releaseWaitlistHold() {
        if (heldWaitlistCount > 0) {
            heldWaitlistCount--;
        }
    }

    public void releaseBookedCnf() {
        if (confirmedCount > 0) {
            confirmedCount--;
        }
    }

    public void releaseBookedRac() {
        if (racCount > 0) {
            racCount--;
        }
    }

    public void releaseBookedWaitlist() {
        if (waitlistCount > 0) {
            waitlistCount--;
        }
    }

    public void promoteRacToCnf() {
        if (racCount > 0) {
            racCount--;
        }
        confirmedCount++;
    }

    public void promoteWaitlistToRac() {
        if (waitlistCount > 0) {
            waitlistCount--;
        }
        racCount++;
    }

    public void clearRemainingWaitlistOnCharting() {
        waitlistCount = 0;
        heldWaitlistCount = 0;
    }
}
