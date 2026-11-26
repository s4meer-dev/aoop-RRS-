package com.irctc.rrs;

import com.irctc.rrs.booking.BookingRequest;
import com.irctc.rrs.booking.BookingRequestBuilder;
import com.irctc.rrs.booking.BookingService;
import com.irctc.rrs.booking.BookingService.BookingResponse;
import com.irctc.rrs.common.exception.PaymentConflictException;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.payment.PaymentService;
import com.irctc.rrs.payment.PaymentService.PaymentCommand;
import com.irctc.rrs.payment.PaymentService.PaymentMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FaultTolerantPaymentTests {

    @Autowired
    BookingService bookingService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    TimeProvider timeProvider;

    @Test
    void idempotencyKeyPreventsDuplicateBookingsAndDoubleCharges() {
        LocalDate date = timeProvider.currentDate();
        String key = "fixed-idem-key-101";

        BookingRequest req = BookingRequestBuilder.newBooking()
                .withUser("pay-user-1")
                .withTrain("12301", date)
                .withRoute("HWH", "NDLS")
                .withClassAndQuota(CoachClass.AC_3, QuotaCode.GN)
                .addPassenger("Ananya", 29, Gender.FEMALE, BerthPreference.LOWER)
                .withIdempotencyKey(key)
                .build();

        BookingResponse first = bookingService.createBooking(req);
        BookingResponse retry = bookingService.createBooking(req);

        assertThat(first.idempotentReplay()).isFalse();
        assertThat(retry.idempotentReplay()).isTrue();
        assertThat(retry.pnrNumber()).isEqualTo(first.pnrNumber());
        assertThat(retry.paymentId()).isEqualTo(first.paymentId());

        assertThatThrownBy(() -> paymentService.processPayment(
                new PaymentCommand("OTHER-PNR", key, Money.of(9999), PaymentMode.NORMAL_SUCCESS)))
                .isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void gatewayTimeoutReconcilesToConfirmedWhenHoldValid() {
        LocalDate date = timeProvider.currentDate();
        BookingRequest req = BookingRequestBuilder.newBooking()
                .withUser("timeout-user-ok")
                .withTrain("12301", date)
                .withRoute("HWH", "NDLS")
                .withClassAndQuota(CoachClass.AC_3, QuotaCode.GN)
                .addPassenger("Vikram", 34, Gender.MALE, BerthPreference.NONE)
                .withPaymentMode(PaymentMode.GATEWAY_TIMEOUT_THEN_SUCCESS)
                .build();

        BookingResponse pending = bookingService.createBooking(req);
        assertThat(pending.status()).isEqualTo(TicketStatus.PAYMENT_PENDING);
        assertThat(pending.paymentStatus()).isEqualTo("TIMEOUT_PENDING");

        paymentService.reconcilePendingPayments();

        BookingResponse after = bookingService.getBooking(pending.pnrNumber());
        assertThat(after.status()).isEqualTo(TicketStatus.CONFIRMED);
        assertThat(after.paymentStatus()).isEqualTo("RECONCILED_SUCCESS");
    }

    @Test
    void lateBankSuccessAfterHoldExpiryTriggersAutomatedSagaRefund() {
        LocalDate date = timeProvider.currentDate();
        BookingRequest req = BookingRequestBuilder.newBooking()
                .withUser("saga-refund-user")
                .withTrain("12301", date)
                .withRoute("HWH", "NDLS")
                .withClassAndQuota(CoachClass.AC_3, QuotaCode.GN)
                .addPassenger("Meera", 31, Gender.FEMALE, BerthPreference.NONE)
                .withPaymentMode(PaymentMode.SLOW_SUCCESS_AFTER_HOLD_EXPIRY)
                .build();

        BookingResponse pending = bookingService.createBooking(req);
        assertThat(pending.status()).isEqualTo(TicketStatus.PAYMENT_PENDING);

        paymentService.reconcilePendingPayments();

        BookingResponse compensated = bookingService.getBooking(pending.pnrNumber());
        assertThat(compensated.status()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(compensated.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(paymentService.listRecentRefunds(10))
                .anyMatch(r -> r.pnrNumber().equals(pending.pnrNumber()) && r.refundAmount().equals(pending.totalFare()));
    }
}
