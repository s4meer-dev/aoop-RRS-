package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.model.BerthPreference;
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
public class SeniorCitizenQuotaStrategy implements QuotaStrategy {

    @Override
    public QuotaCode getQuotaCode() {
        return QuotaCode.SS;
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
        if (passengers == null || passengers.isEmpty() || passengers.size() > 2) {
            throw new QuotaEligibilityException("Senior Citizen quota (SS) supports 1 to 2 passengers per booking.");
        }
        boolean allSenior = passengers.stream().allMatch(this::isSeniorCitizen);
        if (!allSenior) {
            throw new QuotaEligibilityException("Senior Citizen quota requires Male age >= 60 or Female/Other age >= 58.");
        }
    }

    @Override
    public BerthPreference adjustPreference(BerthPreference requestedPreference) {
        // Automatically prioritize Lower Berth for senior citizens when unspecified
        if (requestedPreference == null || requestedPreference == BerthPreference.NONE) {
            return BerthPreference.LOWER;
        }
        return requestedPreference;
    }

    @Override
    public Money calculateTotalFare(Money baseFarePerPassenger, int passengerCount) {
        // 15% senior citizen concession
        return baseFarePerPassenger.multiply(0.85).multiply(passengerCount);
    }

    @Override
    public Money calculateCancellationRefund(Money paidFare, TicketStatus statusAtCancel) {
        return switch (statusAtCancel) {
            case CONFIRMED -> paidFare.percentage(85.0);
            case RAC -> paidFare.percentage(92.0);
            case WAITLISTED -> paidFare.percentage(95.0);
            default -> Money.ZERO;
        };
    }

    private boolean isSeniorCitizen(PassengerEligibilityInfo p) {
        return p.gender() == Gender.MALE ? p.age() >= 60 : p.age() >= 58;
    }
}
