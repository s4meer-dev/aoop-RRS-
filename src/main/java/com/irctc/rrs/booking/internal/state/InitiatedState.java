package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.TicketStatus;

public class InitiatedState implements TicketState {

    @Override
    public TicketStatus getStatus() {
        return TicketStatus.INITIATED;
    }

    @Override
    public void onHoldAcquired(Ticket ticket, String details) {
        ticket.transitionTo(new PaymentPendingState(), details);
    }

    @Override
    public void onExpire(Ticket ticket, String reason) {
        ticket.transitionTo(new ExpiredState(), reason);
    }
}
