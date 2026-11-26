package com.irctc.rrs.inventory;

import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Public service contract of the Inventory Bounded Context (Unit 7).
 */
public interface InventoryService {

    List<TrainSummary> listTrains();

    AvailabilitySnapshot getAvailability(PartitionKey key);

    List<BerthSnapshot> getCoachLayout(String trainNumber, LocalDate journeyDate, CoachClass coachClass);

    HoldResult acquireHoldBatch(
            PartitionKey key,
            List<PassengerEligibilityInfo> passengers,
            List<BerthPreference> preferences,
            boolean bypassTatkalClock
    );

    void commitHeldAllocations(PartitionKey key, List<HeldTicketAssignment> assignments);

    void releaseExpiredOrFailedHolds(PartitionKey key, List<SeatHoldReceipt> holds);

    boolean assignFreedBerthToRacUpgrade(PartitionKey key, String coachCode, int berthNumber, String upgradedTicketId);

    boolean assignRacSlotToWaitlistUpgrade(PartitionKey key, String upgradedTicketId, String preferredSlotRef);

    BerthReleaseResult releaseBookedSlot(PartitionKey key, TicketStatus currentStatus, String ticketId, String coachCode, Integer berthNumber);

    Money calculateRefund(QuotaCode quotaCode, Money paidFare, TicketStatus statusAtCancel);

    ChartSummary prepareChart(String trainNumber, LocalDate journeyDate);

    void ensureSimulationJourney(String trainNumber, LocalDate journeyDate, int cnfSlotsPerClass, int racSlotsPerClass, int maxWlLimit);

    record TrainSummary(
            String number,
            String name,
            String fromStationCode,
            String fromStationName,
            String toStationCode,
            String toStationName,
            int distanceKm,
            String duration,
            String type,
            String zone,
            String departure,
            String arrival,
            List<CoachClass> classes
    ) {}

    record AvailabilitySnapshot(
            PartitionKey partitionKey,
            int availableCnf,
            int totalCnf,
            int availableRac,
            int totalRac,
            int currentWaitlist,
            int maxWaitlist,
            boolean chartPrepared,
            Money farePerPassenger
    ) {}

    record BerthSnapshot(
            String coachCode,
            int berthNumber,
            String berthType,
            String occupancyState,
            List<String> occupantTicketIds,
            String heldByHoldId
    ) {}

    enum HoldAllocationType {
        CNF_HOLD,
        RAC_HOLD,
        WL_RESERVATION
    }

    record SeatHoldReceipt(
            String holdId,
            HoldAllocationType allocationType,
            String coachCode,
            Integer berthNumber,
            String berthType,
            int tentativeQueueNumber,
            Instant expiresAt
    ) {}

    record HoldResult(
            PartitionKey partitionKey,
            Money totalFare,
            List<SeatHoldReceipt> holds
    ) {}

    record HeldTicketAssignment(
            SeatHoldReceipt hold,
            String ticketId
    ) {}

    record PassengerEligibilityInfo(
            String name,
            int age,
            Gender gender
    ) {}

    record BerthReleaseResult(
            boolean freedFullBerth,
            boolean freedRacSlot,
            String coachCode,
            Integer berthNumber
    ) {}

    record ChartSummary(
            String trainNumber,
            LocalDate journeyDate,
            int totalCoaches,
            int confirmedCount,
            int racCount,
            Instant preparedAt
    ) {}
}
