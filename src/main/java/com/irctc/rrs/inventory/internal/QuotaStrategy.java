package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.inventory.InventoryService.PassengerEligibilityInfo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Strategy Pattern contract eliminating hardcoded quota if-else conditionals (Slide 3 & Slide 4).
 */
public interface QuotaStrategy {

    QuotaCode getQuotaCode();

    void validateEligibility(
            List<PassengerEligibilityInfo> passengers,
            CoachClass coachClass,
            LocalDate journeyDate,
            LocalDate currentDate,
            LocalTime currentTime,
            boolean enforceTatkalWindow
    );

    default BerthPreference adjustPreference(BerthPreference requestedPreference) {
        return requestedPreference == null ? BerthPreference.NONE : requestedPreference;
    }

    Money calculateTotalFare(Money baseFarePerPassenger, int passengerCount);

    Money calculateCancellationRefund(Money paidFare, TicketStatus statusAtCancel);
}
