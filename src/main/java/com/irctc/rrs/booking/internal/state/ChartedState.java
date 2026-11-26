package com.irctc.rrs.booking.internal.state;

import com.irctc.rrs.common.model.TicketStatus;

public class ChartedState implements TicketState {

    @Override
    public TicketStatus getStatus() {
        return TicketStatus.CHARTED;
    }
}
