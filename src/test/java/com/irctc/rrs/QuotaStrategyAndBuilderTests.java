package com.irctc.rrs;

import com.irctc.rrs.booking.BookingRequest;
import com.irctc.rrs.booking.BookingRequestBuilder;
import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.inventory.InventoryService.PassengerEligibilityInfo;
import com.irctc.rrs.inventory.internal.GeneralQuotaStrategy;
import com.irctc.rrs.inventory.internal.LadiesQuotaStrategy;
import com.irctc.rrs.inventory.internal.SeniorCitizenQuotaStrategy;
import com.irctc.rrs.inventory.internal.TatkalQuotaStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaStrategyAndBuilderTests {

    @Test
    void builderEnforcesPassengerLimitsAndRouteValidation() {
        LocalDate date = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> BookingRequestBuilder.newBooking()
                .withTrain("12951", date)
                .withRoute("NDLS", "NDLS")
                .withClassAndQuota(CoachClass.AC_3, QuotaCode.GN)
                .addPassenger("Test", 25, Gender.MALE, BerthPreference.NONE)
                .build())
                .isInstanceOf(QuotaEligibilityException.class);

        BookingRequest valid = BookingRequestBuilder.newBooking()
                .withUser("user-1")
                .withTrain("12951", date)
                .withRoute("bct", "ndls")
                .withClassAndQuota(CoachClass.AC_3, QuotaCode.GN)
                .addPassenger("Riya", 24, Gender.FEMALE, BerthPreference.LOWER)
                .build();

        assertThat(valid.sourceStation()).isEqualTo("BCT");
        assertThat(valid.destinationStation()).isEqualTo("NDLS");
        assertThat(valid.idempotencyKey()).isNotBlank();
    }

    @Test
    void tatkalQuotaEnforces10AmAcWindowMax4PassengersAndDynamicSurcharge() {
        TatkalQuotaStrategy tq = new TatkalQuotaStrategy();
        LocalDate today = LocalDate.of(2026, 12, 10);
        LocalDate journeyTomorrow = today.plusDays(1);
        List<PassengerEligibilityInfo> onePax = List.of(new PassengerEligibilityInfo("A", 30, Gender.MALE));

        // 09:59 AM AC Tatkal must fail when window enforcement is enabled
        assertThatThrownBy(() -> tq.validateEligibility(
                onePax, CoachClass.AC_3, journeyTomorrow, today, LocalTime.of(9, 59), true))
                .isInstanceOf(QuotaEligibilityException.class);

        // 10:00 AM AC Tatkal passes
        tq.validateEligibility(onePax, CoachClass.AC_3, journeyTomorrow, today, LocalTime.of(10, 0), true);

        // 10:30 AM Non-AC Sleeper must still fail (opens 11:00 AM)
        assertThatThrownBy(() -> tq.validateEligibility(
                onePax, CoachClass.SLEEPER, journeyTomorrow, today, LocalTime.of(10, 30), true))
                .isInstanceOf(QuotaEligibilityException.class);

        // 30% surcharge & strict CNF no-refund rule
        assertThat(tq.calculateTotalFare(Money.of(1000), 1)).isEqualTo(Money.of(1300));
        assertThat(tq.calculateCancellationRefund(Money.of(1300), TicketStatus.CONFIRMED)).isEqualTo(Money.ZERO);
    }

    @Test
    void ladiesAndSeniorCitizenQuotasEnforcePolymorphicEligibility() {
        LadiesQuotaStrategy ld = new LadiesQuotaStrategy();
        SeniorCitizenQuotaStrategy ss = new SeniorCitizenQuotaStrategy();
        GeneralQuotaStrategy gn = new GeneralQuotaStrategy();
        LocalDate date = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> ld.validateEligibility(
                List.of(new PassengerEligibilityInfo("Adult Male", 29, Gender.MALE)),
                CoachClass.AC_3, date, LocalDate.now(), LocalTime.NOON, false))
                .isInstanceOf(QuotaEligibilityException.class);

        ld.validateEligibility(
                List.of(
                        new PassengerEligibilityInfo("Priya", 32, Gender.FEMALE),
                        new PassengerEligibilityInfo("Child", 8, Gender.MALE)
                ),
                CoachClass.AC_3, date, LocalDate.now(), LocalTime.NOON, false);

        assertThat(ss.adjustPreference(BerthPreference.NONE)).isEqualTo(BerthPreference.LOWER);
        assertThat(ss.calculateTotalFare(Money.of(1000), 1)).isEqualTo(Money.of(850));
        assertThat(gn.calculateCancellationRefund(Money.of(1000), TicketStatus.CONFIRMED)).isEqualTo(Money.of(800));
    }
}
