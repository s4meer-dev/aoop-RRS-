package com.irctc.rrs.common.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Partition key isolating locks and inventory pools to a single
 * (Train, JourneyDate, CoachClass, QuotaCode) slice instead of global DB locking.
 */
public record PartitionKey(
        String trainNumber,
        LocalDate journeyDate,
        CoachClass coachClass,
        QuotaCode quotaCode
) {
    public PartitionKey {
        Objects.requireNonNull(trainNumber, "trainNumber must not be null");
        Objects.requireNonNull(journeyDate, "journeyDate must not be null");
        Objects.requireNonNull(coachClass, "coachClass must not be null");
        Objects.requireNonNull(quotaCode, "quotaCode must not be null");
    }

    public String asString() {
        return trainNumber + ":" + journeyDate + ":" + coachClass.getCode() + ":" + quotaCode.name();
    }
}
