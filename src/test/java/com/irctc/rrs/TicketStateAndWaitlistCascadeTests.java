package com.irctc.rrs;

import com.irctc.rrs.booking.BookingRequestBuilder;
import com.irctc.rrs.booking.BookingService;
import com.irctc.rrs.booking.BookingService.BookingResponse;
import com.irctc.rrs.booking.BookingService.CancellationResponse;
import com.irctc.rrs.booking.BookingService.ChartFinalizeResponse;
import com.irctc.rrs.booking.internal.state.Ticket;
import com.irctc.rrs.common.exception.InvalidStateTransitionException;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.inventory.InventoryService;
import com.irctc.rrs.waitlist.WaitlistService;
import com.irctc.rrs.waitlist.WaitlistService.QueueSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TicketStateAndWaitlistCascadeTests {

    @Autowired
    BookingService bookingService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    WaitlistService waitlistService;

    @Autowired
    TimeProvider timeProvider;

    @Test
    void ticketStatePatternBlocksIllegalTransitions() {
        Ticket ticket = new Ticket("T1", "PNR1", "Test", 30, Gender.MALE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.INITIATED);
        assertThatThrownBy(() -> ticket.cancel("Cannot cancel before hold"))
                .isInstanceOf(InvalidStateTransitionException.class);

        ticket.markHoldAcquired("Hold 1");
        ticket.confirm("B1", 1, "LB", "Confirmed");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CONFIRMED);

        ticket.finalizeChart();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CHARTED);
        assertThatThrownBy(() -> ticket.cancel("Cannot cancel after chart"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void confirmedCancellationTriggersGapFreeRacAndWaitlistCascade() {
        LocalDate testDate = timeProvider.currentDate().plusDays(15);
        inventoryService.ensureSimulationJourney("12951", testDate, 2, 2, 10);
        PartitionKey key = new PartitionKey("12951", testDate, CoachClass.AC_3, QuotaCode.GN);

        var availability = inventoryService.getAvailability(key);
        int cnfCount = availability.totalCnf();
        int racCount = availability.totalRac();

        List<BookingResponse> cnfBookings = new ArrayList<>();
        for (int i = 0; i < cnfCount; i++) {
            cnfBookings.add(bookOne("12951", testDate, "CNF-Pax-" + i));
        }
        assertThat(cnfBookings).allMatch(b -> b.status() == TicketStatus.CONFIRMED);

        List<BookingResponse> racBookings = new ArrayList<>();
        for (int i = 0; i < racCount; i++) {
            racBookings.add(bookOne("12951", testDate, "RAC-Pax-" + i));
        }
        assertThat(racBookings).allMatch(b -> b.status() == TicketStatus.RAC);

        BookingResponse wl1 = bookOne("12951", testDate, "WL-Pax-1");
        BookingResponse wl2 = bookOne("12951", testDate, "WL-Pax-2");
        BookingResponse wl3 = bookOne("12951", testDate, "WL-Pax-3");
        assertThat(wl1.tickets().get(0).queuePosition()).isEqualTo(1);
        assertThat(wl2.tickets().get(0).queuePosition()).isEqualTo(2);
        assertThat(wl3.tickets().get(0).queuePosition()).isEqualTo(3);

        // Cancel first Confirmed PNR -> RAC #1 becomes CONFIRMED, WL #1 enters RAC, WL #2 & #3 shift to #1 & #2
        CancellationResponse cancelRes = bookingService.cancelBooking(cnfBookings.get(0).pnrNumber(), "Plan changed");
        assertThat(cancelRes.status()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelRes.queueContiguousValid()).isTrue();
        assertThat(cancelRes.cascadeUpgrades()).hasSize(2);

        BookingResponse rac1Updated = bookingService.getBooking(racBookings.get(0).pnrNumber());
        BookingResponse wl1Updated = bookingService.getBooking(wl1.pnrNumber());
        BookingResponse wl2Updated = bookingService.getBooking(wl2.pnrNumber());
        BookingResponse wl3Updated = bookingService.getBooking(wl3.pnrNumber());

        assertThat(rac1Updated.status()).isEqualTo(TicketStatus.CONFIRMED);
        assertThat(wl1Updated.status()).isEqualTo(TicketStatus.RAC);
        assertThat(wl2Updated.status()).isEqualTo(TicketStatus.WAITLISTED);
        assertThat(wl2Updated.tickets().get(0).queuePosition()).isEqualTo(1);
        assertThat(wl3Updated.tickets().get(0).queuePosition()).isEqualTo(2);

        QueueSnapshot queue = waitlistService.getQueueSnapshot(key);
        assertThat(queue.contiguousInvariantValid()).isTrue();

        // Prepare final chart -> remaining CNF/RAC become CHARTED, WL #1 & #2 auto-cancelled & refunded
        ChartFinalizeResponse chart = bookingService.prepareTrainChart("12951", testDate);
        assertThat(chart.droppedWaitlistCount()).isEqualTo(2);
        assertThat(chart.autoRefundsTriggered()).isEqualTo(2);
        assertThat(bookingService.getBooking(wl2.pnrNumber()).status()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(bookingService.getBooking(rac1Updated.pnrNumber()).status()).isEqualTo(TicketStatus.CHARTED);
    }

    private BookingResponse bookOne(String train, LocalDate date, String name) {
        return bookingService.createBooking(BookingRequestBuilder.newBooking()
                .withUser("cascade-tester")
                .withTrain(train, date)
                .withRoute("BCT", "NDLS")
                .withClassAndQuota(CoachClass.AC_3, QuotaCode.GN)
                .addPassenger(name, 26, Gender.MALE, BerthPreference.NONE)
                .bypassRateLimit(true)
                .build());
    }
}
