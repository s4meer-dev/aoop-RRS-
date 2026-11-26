package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.exception.InvalidStateTransitionException;
import com.irctc.rrs.common.model.TicketStatus;

/**
 * State Pattern contract governing legal transitions in the Ticket lifecycle (Slide 4 & Slide 6).
 */
public interface TicketState {

    TicketStatus getStatus();

    default void onHoldAcquired(Ticket ticket, String details) {
        throw illegal("acquire hold");
    }

    default void onConfirm(Ticket ticket, String coachCode, int berthNumber, String berthType, String details) {
        throw illegal("confirm");
    }

    default void onAssignRac(Ticket ticket, String coachCode, int berthNumber, int racPosition, String details) {
        throw illegal("assign RAC");
    }

    default void onAssignWaitlist(Ticket ticket, int wlPosition, String details) {
        throw illegal("assign Waitlist");
    }

    default void onUpgradeToRac(Ticket ticket, String coachCode, int berthNumber, int racPosition, String details) {
        throw illegal("upgrade to RAC");
    }

    default void onUpgradeToConfirmed(Ticket ticket, String coachCode, int berthNumber, String details) {
        throw illegal("upgrade to Confirmed");
    }

    default void onCancel(Ticket ticket, String reason) {
        throw illegal("cancel");
    }

    default void onExpire(Ticket ticket, String reason) {
        throw illegal("expire");
    }

    default void onChartPrepared(Ticket ticket) {
        throw illegal("finalize chart");
    }

    private InvalidStateTransitionException illegal(String action) {
        return new InvalidStateTransitionException(
                "Cannot " + action + " ticket when in state " + getStatus());
    }
}
