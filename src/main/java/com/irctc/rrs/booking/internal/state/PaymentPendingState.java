package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.TicketStatus;

public class PaymentPendingState implements TicketState {

    @Override
    public TicketStatus getStatus() {
        return TicketStatus.PAYMENT_PENDING;
    }

    @Override
    public void onConfirm(Ticket ticket, String coachCode, int berthNumber, String berthType, String details) {
        ticket.setAllocation(coachCode, berthNumber, berthType, 0);
        ticket.transitionTo(new ConfirmedState(), details);
    }

    @Override
    public void onAssignRac(Ticket ticket, String coachCode, int berthNumber, int racPosition, String details) {
        ticket.setAllocation(coachCode, berthNumber, "SL", racPosition);
        ticket.transitionTo(new RacState(), details);
    }

    @Override
    public void onAssignWaitlist(Ticket ticket, int wlPosition, String details) {
        ticket.setAllocation(null, null, "WL", wlPosition);
        ticket.transitionTo(new WaitlistedState(), details);
    }

    @Override
    public void onCancel(Ticket ticket, String reason) {
        ticket.clearAllocation();
        ticket.transitionTo(new CancelledState(), reason);
    }

    @Override
    public void onExpire(Ticket ticket, String reason) {
        ticket.clearAllocation();
        ticket.transitionTo(new ExpiredState(), reason);
    }
}
