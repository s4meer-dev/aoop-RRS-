package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.inventory.InventoryService.PassengerEligibilityInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class LadiesQuotaStrategy implements QuotaStrategy {

    @Override
    public QuotaCode getQuotaCode() {
        return QuotaCode.LD;
    }

    @Override
    public void validateEligibility(
            List<PassengerEligibilityInfo> passengers,
            CoachClass coachClass,
            LocalDate journeyDate,
            LocalDate currentDate,
            LocalTime currentTime,
            boolean enforceTatkalWindow
    ) {
        if (passengers == null || passengers.isEmpty() || passengers.size() > 6) {
            throw new QuotaEligibilityException("Ladies quota requires 1 to 6 passengers.");
        }
        boolean allEligible = passengers.stream()
                .allMatch(p -> p.gender() == Gender.FEMALE || p.age() <= 12);
        if (!allEligible) {
            throw new QuotaEligibilityException("Ladies quota (LD) is restricted to female passengers or children aged <= 12.");
        }
    }

    @Override
    public Money calculateTotalFare(Money baseFarePerPassenger, int passengerCount) {
        return baseFarePerPassenger.multiply(passengerCount);
    }

    @Override
    public Money calculateCancellationRefund(Money paidFare, TicketStatus statusAtCancel) {
        return switch (statusAtCancel) {
            case CONFIRMED -> paidFare.percentage(85.0);
            case RAC -> paidFare.percentage(90.0);
            case WAITLISTED -> paidFare.percentage(95.0);
            default -> Money.ZERO;
        };
    }
}
