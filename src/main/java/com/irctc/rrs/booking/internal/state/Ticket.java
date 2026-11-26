package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.TicketStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulated passenger ticket delegating lifecycle rules to TicketState.
 */
public class Ticket {

    public record TransitionRecord(TicketStatus from, TicketStatus to, String details, Instant timestamp) {}

    private final String ticketId;
    private final String pnrNumber;
    private final String passengerName;
    private final int age;
    private final Gender gender;

    private TicketState currentState;
    private String allocatedCoach;
    private Integer allocatedBerth;
    private String berthType;
    private int queuePosition;
    private final List<TransitionRecord> history = new ArrayList<>();

    public Ticket(String ticketId, String pnrNumber, String passengerName, int age, Gender gender) {
        this.ticketId = ticketId;
        this.pnrNumber = pnrNumber;
        this.passengerName = passengerName;
        this.age = age;
        this.gender = gender;
        this.currentState = new InitiatedState();
        this.history.add(new TransitionRecord(null, TicketStatus.INITIATED, "Ticket created via Builder", Instant.now()));
    }

    public synchronized void markHoldAcquired(String details) {
        currentState.onHoldAcquired(this, details);
    }

    public synchronized void confirm(String coachCode, int berthNumber, String berthType, String details) {
        currentState.onConfirm(this, coachCode, berthNumber, berthType, details);
    }

    public synchronized void assignRac(String coachCode, int berthNumber, int racPosition, String details) {
        currentState.onAssignRac(this, coachCode, berthNumber, racPosition, details);
    }

    public synchronized void assignWaitlist(int wlPosition, String details) {
        currentState.onAssignWaitlist(this, wlPosition, details);
    }

    public synchronized void upgradeToRac(String coachCode, int berthNumber, int racPosition, String details) {
        currentState.onUpgradeToRac(this, coachCode, berthNumber, racPosition, details);
    }

    public synchronized void upgradeToConfirmed(String coachCode, int berthNumber, String details) {
        currentState.onUpgradeToConfirmed(this, coachCode, berthNumber, details);
    }

    public synchronized void updateContiguousPosition(int newPosition) {
        this.queuePosition = newPosition;
    }

    public synchronized void cancel(String reason) {
        currentState.onCancel(this, reason);
    }

    public synchronized void expire(String reason) {
        currentState.onExpire(this, reason);
    }

    public synchronized void finalizeChart() {
        currentState.onChartPrepared(this);
    }

    void transitionTo(TicketState next, String details) {
        TicketStatus previous = this.currentState.getStatus();
        this.currentState = next;
        this.history.add(new TransitionRecord(previous, next.getStatus(), details, Instant.now()));
    }

    void setAllocation(String coachCode, Integer berthNumber, String berthType, int queuePosition) {
        this.allocatedCoach = coachCode;
        this.allocatedBerth = berthNumber;
        this.berthType = berthType;
        this.queuePosition = queuePosition;
    }

    void clearAllocation() {
        this.allocatedCoach = null;
        this.allocatedBerth = null;
        this.queuePosition = 0;
    }

    public synchronized String getTicketId() {
        return ticketId;
    }

    public synchronized String getPnrNumber() {
        return pnrNumber;
    }

    public synchronized String getPassengerName() {
        return passengerName;
    }

    public synchronized int getAge() {
        return age;
    }

    public synchronized Gender getGender() {
        return gender;
    }

    public synchronized TicketStatus getStatus() {
        return currentState.getStatus();
    }

    public synchronized String getAllocatedCoach() {
        return allocatedCoach;
    }

    public synchronized Integer getAllocatedBerth() {
        return allocatedBerth;
    }

    public synchronized String getBerthType() {
        return berthType;
    }

    public synchronized int getQueuePosition() {
        return queuePosition;
    }

    public synchronized List<TransitionRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
