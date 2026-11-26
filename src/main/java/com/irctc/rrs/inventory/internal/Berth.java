package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.BerthType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulated physical berth enforcing the Zero-Overbooking Invariant:
 * - Full CNF allows at most 1 occupant.
 * - Side-Lower RAC allows at most 2 occupants sharing the berth.
 */
public class Berth {

    public enum OccupancyState {
        AVAILABLE,
        HELD_CNF,
        CONFIRMED,
        RAC_PARTIAL,
        RAC_FULL
    }

    private final String coachCode;
    private final int berthNumber;
    private final BerthType berthType;
    private final boolean racShareable;

    private OccupancyState occupancyState = OccupancyState.AVAILABLE;
    private final List<String> occupantTicketIds = new ArrayList<>(2);
    private String activeHoldId;
    private Instant holdExpiresAt;
    private int heldRacSlots = 0;

    public Berth(String coachCode, int berthNumber, BerthType berthType, boolean racShareable) {
        this.coachCode = coachCode;
        this.berthNumber = berthNumber;
        this.berthType = berthType;
        this.racShareable = racShareable;
    }

    public String getCoachCode() {
        return coachCode;
    }

    public int getBerthNumber() {
        return berthNumber;
    }

    public BerthType getBerthType() {
        return berthType;
    }

    public boolean isRacShareable() {
        return racShareable;
    }

    public OccupancyState getOccupancyState() {
        return occupancyState;
    }

    public List<String> getOccupantTicketIds() {
        return Collections.unmodifiableList(occupantTicketIds);
    }

    public String getActiveHoldId() {
        return activeHoldId;
    }

    public boolean isAvailableForCnf(Instant now) {
        releaseIfExpired(now);
        return !racShareable
                && occupancyState == OccupancyState.AVAILABLE
                && occupantTicketIds.isEmpty()
                && activeHoldId == null;
    }

    public boolean isAvailableForRac(Instant now) {
        releaseIfExpired(now);
        if (!racShareable) {
            return false;
        }
        return (occupantTicketIds.size() + heldRacSlots) < 2 && occupancyState != OccupancyState.CONFIRMED && occupancyState != OccupancyState.HELD_CNF;
    }

    public boolean acquireCnfHold(String holdId, Instant expiresAt, Instant now) {
        if (!isAvailableForCnf(now)) {
            return false;
        }
        this.occupancyState = OccupancyState.HELD_CNF;
        this.activeHoldId = holdId;
        this.holdExpiresAt = expiresAt;
        return true;
    }

    public boolean acquireRacHold(String holdId, Instant expiresAt, Instant now) {
        if (!isAvailableForRac(now)) {
            return false;
        }
        this.heldRacSlots++;
        this.activeHoldId = holdId;
        this.holdExpiresAt = expiresAt;
        return true;
    }

    public void commitCnfHold(String holdId, String ticketId) {
        if (occupancyState != OccupancyState.HELD_CNF || !holdId.equals(activeHoldId)) {
            throw new IllegalStateException("Berth " + coachCode + "-" + berthNumber + " is not held by " + holdId);
        }
        this.activeHoldId = null;
        this.holdExpiresAt = null;
        this.occupancyState = OccupancyState.CONFIRMED;
        this.occupantTicketIds.clear();
        this.occupantTicketIds.add(ticketId);
    }

    public void commitRacHold(String ticketId) {
        if (heldRacSlots > 0) {
            heldRacSlots--;
        }
        if (heldRacSlots == 0) {
            this.activeHoldId = null;
            this.holdExpiresAt = null;
        }
        if (occupantTicketIds.size() >= 2) {
            throw new IllegalStateException("RAC berth " + coachCode + "-" + berthNumber + " already has 2 passengers");
        }
        occupantTicketIds.add(ticketId);
        this.occupancyState = occupantTicketIds.size() == 2 ? OccupancyState.RAC_FULL : OccupancyState.RAC_PARTIAL;
    }

    public void releaseHold(String holdId, boolean racHold) {
        if (racHold) {
            if (heldRacSlots > 0) {
                heldRacSlots--;
            }
            if (heldRacSlots == 0 && holdId.equals(activeHoldId)) {
                this.activeHoldId = null;
                this.holdExpiresAt = null;
            }
            return;
        }
        if (occupancyState == OccupancyState.HELD_CNF && holdId.equals(activeHoldId)) {
            this.occupancyState = OccupancyState.AVAILABLE;
            this.activeHoldId = null;
            this.holdExpiresAt = null;
        }
    }

    public boolean assignUpgradedCnf(String ticketId) {
        if (occupancyState != OccupancyState.AVAILABLE || !occupantTicketIds.isEmpty()) {
            return false;
        }
        this.occupancyState = OccupancyState.CONFIRMED;
        this.occupantTicketIds.add(ticketId);
        return true;
    }

    public boolean assignUpgradedRac(String ticketId) {
        if (!racShareable || occupantTicketIds.size() + heldRacSlots >= 2) {
            return false;
        }
        this.occupantTicketIds.add(ticketId);
        this.occupancyState = occupantTicketIds.size() == 2 ? OccupancyState.RAC_FULL : OccupancyState.RAC_PARTIAL;
        return true;
    }

    public boolean vacateConfirmed(String ticketId) {
        if (occupancyState == OccupancyState.CONFIRMED && occupantTicketIds.remove(ticketId)) {
            this.occupancyState = OccupancyState.AVAILABLE;
            return true;
        }
        return false;
    }

    public boolean vacateRac(String ticketId) {
        if (occupantTicketIds.remove(ticketId)) {
            this.occupancyState = occupantTicketIds.isEmpty() ? OccupancyState.AVAILABLE : OccupancyState.RAC_PARTIAL;
            return true;
        }
        return false;
    }

    private void releaseIfExpired(Instant now) {
        if (holdExpiresAt != null && now.isAfter(holdExpiresAt)) {
            if (occupancyState == OccupancyState.HELD_CNF) {
                occupancyState = OccupancyState.AVAILABLE;
            }
            heldRacSlots = 0;
            activeHoldId = null;
            holdExpiresAt = null;
        }
    }
}
