package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.TicketStatus;

public class RacState implements TicketState {

    @Override
    public TicketStatus getStatus() {
        return TicketStatus.RAC;
    }

    @Override
    public void onUpgradeToConfirmed(Ticket ticket, String coachCode, int berthNumber, String details) {
        ticket.setAllocation(coachCode, berthNumber, "CNF", 0);
        ticket.transitionTo(new ConfirmedState(), details);
    }

    @Override
    public void onCancel(Ticket ticket, String reason) {
        ticket.clearAllocation();
        ticket.transitionTo(new CancelledState(), reason);
    }

    @Override
    public void onChartPrepared(Ticket ticket) {
        ticket.transitionTo(new ChartedState(), "Final chart prepared (RAC boarding confirmed)");
    }
}
