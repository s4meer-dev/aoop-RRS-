package com.irctc.rrs;

import com.irctc.rrs.booking.BookingRequestBuilder;
import com.irctc.rrs.booking.BookingService;
import com.irctc.rrs.booking.BookingService.TatkalSimulationReport;
import com.irctc.rrs.booking.BookingService.TatkalSimulationRequest;
import com.irctc.rrs.common.exception.RateLimitExceededException;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.inventory.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TatkalConcurrencyAndBackpressureTests {

    @Autowired
    BookingService bookingService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    TimeProvider timeProvider;

    @Test
    void concurrentTatkalSurgeGuaranteesZeroDoubleBookingsAndBoundedShedding() {
        TatkalSimulationReport report = bookingService.runTatkalSurgeSimulation(new TatkalSimulationRequest(
                "12951", CoachClass.AC_3, QuotaCode.TQ,
                400, 128, 12, 4, 15, true
        ));

        assertThat(report.totalRequests()).isEqualTo(400);
        assertThat(report.doubleBookedBerthsDetected()).isZero();
        assertThat(report.waitlistContiguousInvariantValid()).isTrue();
        assertThat(report.confirmedBookings()).isGreaterThan(0);
        assertThat(report.admittedCount() + report.shedByBackpressure503()).isEqualTo(400);
    }

    @Test
    void bucket4jRateLimiterBlocksExcessiveBurstFromSingleBotUser() {
        LocalDate date = timeProvider.currentDate().plusDays(20);
        inventoryService.ensureSimulationJourney("12002", date, 80, 0, 50);

        assertThatThrownBy(() -> {
            for (int i = 0; i < 80; i++) {
                bookingService.createBooking(BookingRequestBuilder.newBooking()
                        .withUser("bot-spammer-99")
                        .withTrain("12002", date)
                        .withRoute("NDLS", "BPL")
                        .withClassAndQuota(CoachClass.CHAIR_CAR, QuotaCode.GN)
                        .addPassenger("Bot", 25, Gender.MALE, BerthPreference.NONE)
                        .build());
            }
        }).isInstanceOf(RateLimitExceededException.class);
    }
}
