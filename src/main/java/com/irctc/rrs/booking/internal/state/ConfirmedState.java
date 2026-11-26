package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.TicketStatus;

public class ConfirmedState implements TicketState {

    @Override
    public TicketStatus getStatus() {
        return TicketStatus.CONFIRMED;
    }

    @Override
    public void onCancel(Ticket ticket, String reason) {
        ticket.clearAllocation();
        ticket.transitionTo(new CancelledState(), reason);
    }

    @Override
    public void onChartPrepared(Ticket ticket) {
        ticket.transitionTo(new ChartedState(), "Final chart prepared with full berth");
    }
}
