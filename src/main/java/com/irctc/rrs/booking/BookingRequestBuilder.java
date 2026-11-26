package com.irctc.rrs.booking;

import com.irctc.rrs.booking.BookingRequest.PassengerInput;
import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.payment.PaymentService.PaymentMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builder Pattern assembling and validating complex multi-passenger booking requests (Slide 4).
 */
public class BookingRequestBuilder {

    private String userId = "anonymous-user";
    private String trainNumber;
    private LocalDate journeyDate;
    private String sourceStation;
    private String destinationStation;
    private CoachClass coachClass;
    private QuotaCode quotaCode = QuotaCode.GN;
    private final List<PassengerInput> passengers = new ArrayList<>();
    private String idempotencyKey;
    private PaymentMode paymentMode = PaymentMode.NORMAL_SUCCESS;
    private boolean bypassTatkalClock = false;
    private boolean bypassRateLimit = false;

    public static BookingRequestBuilder newBooking() {
        return new BookingRequestBuilder();
    }

    public BookingRequestBuilder withUser(String userId) {
        if (userId != null && !userId.isBlank()) {
            this.userId = userId.trim();
        }
        return this;
    }

    public BookingRequestBuilder withTrain(String trainNumber, LocalDate journeyDate) {
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        return this;
    }

    public BookingRequestBuilder withRoute(String sourceStation, String destinationStation) {
        this.sourceStation = sourceStation;
        this.destinationStation = destinationStation;
        return this;
    }

    public BookingRequestBuilder withClassAndQuota(CoachClass coachClass, QuotaCode quotaCode) {
        this.coachClass = coachClass;
        this.quotaCode = quotaCode == null ? QuotaCode.GN : quotaCode;
        return this;
    }

    public BookingRequestBuilder addPassenger(String name, int age, Gender gender, BerthPreference preference) {
        this.passengers.add(new PassengerInput(
                name == null ? "" : name.trim(),
                age,
                gender == null ? Gender.OTHER : gender,
                preference == null ? BerthPreference.NONE : preference
        ));
        return this;
    }

    public BookingRequestBuilder withPassengers(List<PassengerInput> inputs) {
        if (inputs != null) {
            inputs.forEach(p -> addPassenger(p.name(), p.age(), p.gender(), p.preference()));
        }
        return this;
    }

    public BookingRequestBuilder withIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public BookingRequestBuilder withPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode == null ? PaymentMode.NORMAL_SUCCESS : paymentMode;
        return this;
    }

    public BookingRequestBuilder bypassTatkalClock(boolean bypass) {
        this.bypassTatkalClock = bypass;
        return this;
    }

    public BookingRequestBuilder bypassRateLimit(boolean bypass) {
        this.bypassRateLimit = bypass;
        return this;
    }

    public BookingRequest build() {
        if (trainNumber == null || trainNumber.isBlank()) {
            throw new QuotaEligibilityException("Train number is required.");
        }
        if (journeyDate == null) {
            throw new QuotaEligibilityException("Journey date is required.");
        }
        if (sourceStation == null || sourceStation.isBlank() || destinationStation == null || destinationStation.isBlank()) {
            throw new QuotaEligibilityException("Source and destination stations are required.");
        }
        if (sourceStation.equalsIgnoreCase(destinationStation)) {
            throw new QuotaEligibilityException("Source and destination stations cannot be identical.");
        }
        if (coachClass == null) {
            throw new QuotaEligibilityException("Coach class is required.");
        }
        if (passengers.isEmpty() || passengers.size() > 6) {
            throw new QuotaEligibilityException("Booking must include between 1 and 6 passengers.");
        }
        for (PassengerInput p : passengers) {
            if (p.name().isBlank()) {
                throw new QuotaEligibilityException("Every passenger must have a non-empty name.");
            }
            if (p.age() < 1 || p.age() > 120) {
                throw new QuotaEligibilityException("Passenger age must be between 1 and 120: " + p.age());
            }
        }
        String effectiveKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? "idem-" + UUID.randomUUID()
                : idempotencyKey.trim();

        return new BookingRequest(
                userId,
                trainNumber.trim(),
                journeyDate,
                sourceStation.trim().toUpperCase(),
                destinationStation.trim().toUpperCase(),
                coachClass,
                quotaCode,
                List.copyOf(passengers),
                effectiveKey,
                paymentMode,
                bypassTatkalClock,
                bypassRateLimit
        );
    }
}
