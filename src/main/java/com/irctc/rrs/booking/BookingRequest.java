package com.irctc.rrs.booking;

import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.payment.PaymentService.PaymentMode;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable validated BookingRequest constructed via BookingRequestBuilder (Builder Pattern).
 */
public record BookingRequest(
        String userId,
        String trainNumber,
        LocalDate journeyDate,
        String sourceStation,
        String destinationStation,
        CoachClass coachClass,
        QuotaCode quotaCode,
        List<PassengerInput> passengers,
        String idempotencyKey,
        PaymentMode paymentMode,
        boolean bypassTatkalClock,
        boolean bypassRateLimit
) {
    public record PassengerInput(
            String name,
            int age,
            Gender gender,
            BerthPreference preference
    ) {}
}
