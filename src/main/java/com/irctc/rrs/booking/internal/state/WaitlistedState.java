package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.TicketStatus;

public class WaitlistedState implements TicketState {

    @Override
    public TicketStatus getStatus() {
        return TicketStatus.WAITLISTED;
    }

    @Override
    public void onUpgradeToRac(Ticket ticket, String coachCode, int berthNumber, int racPosition, String details) {
        ticket.setAllocation(coachCode, berthNumber, "SL", racPosition);
        ticket.transitionTo(new RacState(), details);
    }

    @Override
    public void onCancel(Ticket ticket, String reason) {
        ticket.clearAllocation();
        ticket.transitionTo(new CancelledState(), reason);
    }
}
