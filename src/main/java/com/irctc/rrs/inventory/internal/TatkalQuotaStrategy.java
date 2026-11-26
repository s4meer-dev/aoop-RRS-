package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.inventory.InventoryService.PassengerEligibilityInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class TatkalQuotaStrategy implements QuotaStrategy {

    private static final LocalTime AC_TATKAL_OPEN = LocalTime.of(10, 0);
    private static final LocalTime NON_AC_TATKAL_OPEN = LocalTime.of(11, 0);

    @Override
    public QuotaCode getQuotaCode() {
        return QuotaCode.TQ;
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
        if (passengers == null || passengers.isEmpty() || passengers.size() > 4) {
            throw new QuotaEligibilityException("Tatkal quota allows a maximum of 4 passengers per PNR.");
        }
        if (journeyDate.isBefore(currentDate)) {
            throw new QuotaEligibilityException("Cannot book Tatkal for a past date.");
        }
        if (enforceTatkalWindow) {
            LocalDate tatkalOpenDate = journeyDate.minusDays(1);
            LocalTime openTime = coachClass.isAirConditioned() ? AC_TATKAL_OPEN : NON_AC_TATKAL_OPEN;
            if (currentDate.isBefore(tatkalOpenDate)
                    || (currentDate.equals(tatkalOpenDate) && currentTime.isBefore(openTime))) {
                throw new QuotaEligibilityException(
                        "Tatkal booking for " + coachClass.getCode() + " opens at " + openTime + " on " + tatkalOpenDate);
            }
        }
    }

    @Override
    public Money calculateTotalFare(Money baseFarePerPassenger, int passengerCount) {
        // 30% Tatkal dynamic surcharge
        return baseFarePerPassenger.multiply(1.30).multiply(passengerCount);
    }

    @Override
    public Money calculateCancellationRefund(Money paidFare, TicketStatus statusAtCancel) {
        // IRCTC Tatkal rule: no refund on voluntary Confirmed cancellation; refund on Waitlisted/RAC
        return switch (statusAtCancel) {
            case CONFIRMED -> Money.ZERO;
            case RAC, WAITLISTED -> paidFare.percentage(85.0);
            default -> Money.ZERO;
        };
    }
}
