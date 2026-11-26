package com.irctc.rrs.inventory.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.irctc.rrs.common.concurrency.PartitionLockManager;
import com.irctc.rrs.common.exception.ChartFinalizedException;
import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.exception.ResourceNotFoundException;
import com.irctc.rrs.common.exception.SeatHoldExpiredException;
import com.irctc.rrs.common.id.IdGenerator;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.inventory.InventoryService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final TrainDataLoader dataLoader;
    private final QuotaStrategyRegistry quotaStrategies;
    private final PartitionLockManager lockManager;
    private final TimeProvider timeProvider;
    private final IdGenerator idGenerator;
    private final InventoryProperties properties;

    private final Cache<String, SeatHold> activeHolds;
    private final Map<PartitionKey, Queue<SeatHold>> partitionHoldIndex = new ConcurrentHashMap<>();

    public InventoryServiceImpl(
            TrainDataLoader dataLoader,
            QuotaStrategyRegistry quotaStrategies,
            PartitionLockManager lockManager,
            TimeProvider timeProvider,
            IdGenerator idGenerator,
            InventoryProperties properties
    ) {
        this.dataLoader = dataLoader;
        this.quotaStrategies = quotaStrategies;
        this.lockManager = lockManager;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.activeHolds = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(10, properties.getSeatHoldTtlSeconds())))
                .build();
    }

    @Override
    public List<TrainSummary> listTrains() {
        return dataLoader.getAllTrains().stream()
                .map(t -> new TrainSummary(
                        t.number(), t.name(),
                        t.fromStationCode(), t.fromStationName(),
                        t.toStationCode(), t.toStationName(),
                        t.distanceKm(), t.duration(), t.type(), t.zone(),
                        t.departure(), t.arrival(), t.classes()
                ))
                .toList();
    }

    @Override
    public AvailabilitySnapshot getAvailability(PartitionKey key) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        return lockManager.executeWithLock(key, () -> {
            reapExpiredHoldsLocked(journey, key);
            QuotaPool pool = requirePool(journey, key);
            QuotaStrategy strategy = quotaStrategies.resolve(key.quotaCode());
            Money baseFare = baseFarePerPassenger(journey.getTrain(), key.coachClass());
            Money finalFare = strategy.calculateTotalFare(baseFare, 1);

            return new AvailabilitySnapshot(
                    key,
                    pool.getAvailableCnfSlots(),
                    pool.getTotalCnfSlots(),
                    pool.getAvailableRacSlots(),
                    pool.getTotalRacSlots(),
                    pool.getCurrentWaitlistCount(),
                    pool.getMaxWaitlistLimit(),
                    journey.isChartPrepared(),
                    finalFare
            );
        });
    }

    @Override
    public List<BerthSnapshot> getCoachLayout(String trainNumber, LocalDate journeyDate, CoachClass coachClass) {
        JourneyInstance journey = requireJourney(trainNumber, journeyDate);
        PartitionKey gnKey = new PartitionKey(trainNumber, journeyDate, coachClass, QuotaCode.GN);
        return lockManager.executeWithLock(gnKey, () -> {
            for (QuotaCode code : QuotaCode.values()) {
                PartitionKey qk = new PartitionKey(trainNumber, journeyDate, coachClass, code);
                if (journey.getQuotaPool(qk) != null) {
                    reapExpiredHoldsLocked(journey, qk);
                }
            }
            List<BerthSnapshot> result = new ArrayList<>();
            for (Coach coach : journey.getCoaches(coachClass)) {
                for (Berth b : coach.getBerths()) {
                    result.add(new BerthSnapshot(
                            b.getCoachCode(),
                            b.getBerthNumber(),
                            b.getBerthType().getShortCode(),
                            b.getOccupancyState().name(),
                            b.getOccupantTicketIds(),
                            b.getActiveHoldId()
                    ));
                }
            }
            return result;
        });
    }

    @Override
    public HoldResult acquireHoldBatch(
            PartitionKey key,
            List<PassengerEligibilityInfo> passengers,
            List<BerthPreference> preferences,
            boolean bypassTatkalClock
    ) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        QuotaStrategy strategy = quotaStrategies.resolve(key.quotaCode());
        boolean enforceClock = properties.isEnforceTatkalWindow() && !bypassTatkalClock;

        strategy.validateEligibility(
                passengers,
                key.coachClass(),
                key.journeyDate(),
                timeProvider.currentDate(),
                timeProvider.currentTime(),
                enforceClock
        );

        Money baseFare = baseFarePerPassenger(journey.getTrain(), key.coachClass());
        Money totalFare = strategy.calculateTotalFare(baseFare, passengers.size());

        return lockManager.executeWithLock(key, () -> {
            if (journey.isChartPrepared()) {
                throw new ChartFinalizedException("Final chart already prepared for train " + key.trainNumber() + " on " + key.journeyDate());
            }
            reapExpiredHoldsLocked(journey, key);
            QuotaPool pool = requirePool(journey, key);
            List<Coach> coaches = journey.getCoaches(key.coachClass());

            Instant now = timeProvider.now();
            Instant expiresAt = now.plusSeconds(properties.getSeatHoldTtlSeconds());
            List<SeatHold> acquired = new ArrayList<>();

            try {
                for (int i = 0; i < passengers.size(); i++) {
                    BerthPreference rawPref = (preferences != null && i < preferences.size())
                            ? preferences.get(i)
                            : BerthPreference.NONE;
                    BerthPreference pref = strategy.adjustPreference(rawPref);
                    SeatHold hold = acquireSingleHoldLocked(pool, coaches, key, pref, now, expiresAt);
                    acquired.add(hold);
                }
            } catch (RuntimeException ex) {
                for (SeatHold h : acquired) {
                    rollbackSingleHoldLocked(journey, pool, h);
                }
                throw ex;
            }

            Queue<SeatHold> idxQueue = partitionHoldIndex.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>());
            List<SeatHoldReceipt> receipts = new ArrayList<>(acquired.size());
            for (SeatHold h : acquired) {
                activeHolds.put(h.getHoldId(), h);
                idxQueue.add(h);
                receipts.add(new SeatHoldReceipt(
                        h.getHoldId(),
                        h.getAllocationType(),
                        h.getCoachCode(),
                        h.getBerthNumber(),
                        h.getBerthType(),
                        h.getTentativeQueueNumber(),
                        h.getExpiresAt()
                ));
            }
            return new HoldResult(key, totalFare, receipts);
        });
    }

    @Override
    public void commitHeldAllocations(PartitionKey key, List<HeldTicketAssignment> assignments) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        lockManager.runWithLock(key, () -> {
            QuotaPool pool = requirePool(journey, key);
            Instant now = timeProvider.now();

            for (HeldTicketAssignment item : assignments) {
                SeatHoldReceipt receipt = item.hold();
                SeatHold hold = activeHolds.getIfPresent(receipt.holdId());
                if (hold == null || !hold.isActive() || hold.isExpired(now)) {
                    throw new SeatHoldExpiredException("Seat hold " + receipt.holdId() + " expired before payment completed.");
                }
            }

            for (HeldTicketAssignment item : assignments) {
                SeatHold hold = activeHolds.getIfPresent(item.hold().holdId());
                if (hold == null || !hold.deactivate()) {
                    throw new SeatHoldExpiredException("Seat hold " + item.hold().holdId() + " is no longer active.");
                }
                activeHolds.invalidate(hold.getHoldId());

                switch (hold.getAllocationType()) {
                    case CNF_HOLD -> {
                        Berth berth = findBerth(journey, key.coachClass(), hold.getCoachCode(), hold.getBerthNumber());
                        berth.commitCnfHold(hold.getHoldId(), item.ticketId());
                        pool.commitCnfHold();
                    }
                    case RAC_HOLD -> {
                        Berth berth = findBerth(journey, key.coachClass(), hold.getCoachCode(), hold.getBerthNumber());
                        berth.commitRacHold(item.ticketId());
                        pool.commitRacHold();
                    }
                    case WL_RESERVATION -> pool.commitWaitlistHold();
                }
            }
        });
    }

    @Override
    public void releaseExpiredOrFailedHolds(PartitionKey key, List<SeatHoldReceipt> holds) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        lockManager.runWithLock(key, () -> {
            QuotaPool pool = requirePool(journey, key);
            for (SeatHoldReceipt receipt : holds) {
                SeatHold hold = activeHolds.getIfPresent(receipt.holdId());
                if (hold != null) {
                    rollbackSingleHoldLocked(journey, pool, hold);
                    activeHolds.invalidate(hold.getHoldId());
                }
            }
        });
    }

    @Override
    public boolean assignFreedBerthToRacUpgrade(PartitionKey key, String coachCode, int berthNumber, String upgradedTicketId) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        return lockManager.executeWithLock(key, () -> {
            QuotaPool pool = requirePool(journey, key);
            Berth freedBerth = findBerth(journey, key.coachClass(), coachCode, berthNumber);
            if (!freedBerth.assignUpgradedCnf(upgradedTicketId)) {
                return false;
            }
            // Vacate the upgraded passenger's previous shared Side-Lower RAC berth so WL #1 can take it
            for (Coach coach : journey.getCoaches(key.coachClass())) {
                for (Berth b : coach.getBerths()) {
                    if (b.isRacShareable()) {
                        b.vacateRac(upgradedTicketId);
                    }
                }
            }
            pool.promoteRacToCnf();
            return true;
        });
    }

    @Override
    public boolean assignRacSlotToWaitlistUpgrade(PartitionKey key, String upgradedTicketId, String preferredSlotRef) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        return lockManager.executeWithLock(key, () -> {
            QuotaPool pool = requirePool(journey, key);
            Instant now = timeProvider.now();

            Berth target = null;
            if (preferredSlotRef != null && preferredSlotRef.contains("-")) {
                String[] parts = preferredSlotRef.split("-");
                if (parts.length == 2) {
                    try {
                        Berth candidate = findBerth(journey, key.coachClass(), parts[0], Integer.parseInt(parts[1]));
                        if (candidate.isAvailableForRac(now)) {
                            target = candidate;
                        }
                    } catch (RuntimeException ignored) {
                        target = null;
                    }
                }
            }
            if (target == null) {
                target = journey.getCoaches(key.coachClass()).stream()
                        .flatMap(c -> c.findAvailableRacBerth(now).stream())
                        .findFirst()
                        .orElse(null);
            }
            if (target == null || !target.assignUpgradedRac(upgradedTicketId)) {
                return false;
            }
            pool.promoteWaitlistToRac();
            return true;
        });
    }

    @Override
    public BerthReleaseResult releaseBookedSlot(
            PartitionKey key,
            TicketStatus currentStatus,
            String ticketId,
            String coachCode,
            Integer berthNumber
    ) {
        JourneyInstance journey = requireJourney(key.trainNumber(), key.journeyDate());
        return lockManager.executeWithLock(key, () -> {
            QuotaPool pool = requirePool(journey, key);
            return switch (currentStatus) {
                case CONFIRMED -> {
                    if (coachCode != null && berthNumber != null) {
                        Berth berth = findBerth(journey, key.coachClass(), coachCode, berthNumber);
                        berth.vacateConfirmed(ticketId);
                    }
                    pool.releaseBookedCnf();
                    yield new BerthReleaseResult(true, false, coachCode, berthNumber);
                }
                case RAC -> {
                    if (coachCode != null && berthNumber != null) {
                        Berth berth = findBerth(journey, key.coachClass(), coachCode, berthNumber);
                        berth.vacateRac(ticketId);
                    }
                    pool.releaseBookedRac();
                    yield new BerthReleaseResult(false, true, coachCode, berthNumber);
                }
                case WAITLISTED -> {
                    pool.releaseBookedWaitlist();
                    yield new BerthReleaseResult(false, false, null, null);
                }
                default -> new BerthReleaseResult(false, false, null, null);
            };
        });
    }

    @Override
    public Money calculateRefund(QuotaCode quotaCode, Money paidFare, TicketStatus statusAtCancel) {
        return quotaStrategies.resolve(quotaCode).calculateCancellationRefund(paidFare, statusAtCancel);
    }

    @Override
    public ChartSummary prepareChart(String trainNumber, LocalDate journeyDate) {
        JourneyInstance journey = requireJourney(trainNumber, journeyDate);
        Instant now = timeProvider.now();

        int confirmedCount = 0;
        int racCount = 0;
        int totalCoaches = 0;

        for (CoachClass coachClass : journey.getTrain().classes()) {
            totalCoaches += journey.getCoaches(coachClass).size();
            for (QuotaCode quotaCode : QuotaCode.values()) {
                PartitionKey key = new PartitionKey(trainNumber, journeyDate, coachClass, quotaCode);
                QuotaPool pool = journey.getQuotaPool(key);
                if (pool != null) {
                    int[] counts = lockManager.executeWithLock(key, () -> {
                        reapExpiredHoldsLocked(journey, key);
                        pool.clearRemainingWaitlistOnCharting();
                        return new int[]{pool.getConfirmedCount(), pool.getRacCount()};
                    });
                    confirmedCount += counts[0];
                    racCount += counts[1];
                }
            }
        }
        journey.markChartPrepared(now);
        return new ChartSummary(trainNumber, journeyDate, totalCoaches, confirmedCount, racCount, now);
    }

    @Override
    public void ensureSimulationJourney(
            String trainNumber,
            LocalDate journeyDate,
            int cnfSlotsPerClass,
            int racSlotsPerClass,
            int maxWlLimit
    ) {
        Train train = dataLoader.getTrain(trainNumber);
        if (train == null) {
            throw new ResourceNotFoundException("Unknown train number: " + trainNumber);
        }
        int totalBerthsNeeded = Math.max(12, cnfSlotsPerClass * 2 + Math.max(2, racSlotsPerClass / 2));
        JourneyInstance simInstance = new JourneyInstance(
                train,
                journeyDate,
                1,
                totalBerthsNeeded,
                racSlotsPerClass,
                maxWlLimit
        );
        dataLoader.putSimulationJourney(trainNumber, journeyDate, simInstance);
    }

    private SeatHold acquireSingleHoldLocked(
            QuotaPool pool,
            List<Coach> coaches,
            PartitionKey key,
            BerthPreference preference,
            Instant now,
            Instant expiresAt
    ) {
        String holdId = idGenerator.nextId("HOLD");

        if (pool.getAvailableCnfSlots() > 0) {
            for (Coach coach : coaches) {
                var berthOpt = coach.findAvailableCnfBerth(preference, now);
                if (berthOpt.isPresent() && berthOpt.get().acquireCnfHold(holdId, expiresAt, now)) {
                    pool.holdCnf();
                    Berth b = berthOpt.get();
                    return new SeatHold(holdId, key, HoldAllocationType.CNF_HOLD,
                            b.getCoachCode(), b.getBerthNumber(), b.getBerthType().getShortCode(), 0, expiresAt);
                }
            }
        }

        if (pool.getAvailableRacSlots() > 0) {
            for (Coach coach : coaches) {
                var berthOpt = coach.findAvailableRacBerth(now);
                if (berthOpt.isPresent() && berthOpt.get().acquireRacHold(holdId, expiresAt, now)) {
                    pool.holdRac();
                    Berth b = berthOpt.get();
                    return new SeatHold(holdId, key, HoldAllocationType.RAC_HOLD,
                            b.getCoachCode(), b.getBerthNumber(), b.getBerthType().getShortCode(), 0, expiresAt);
                }
            }
        }

        int tentativeWl = pool.holdWaitlist();
        if (tentativeWl > 0) {
            return new SeatHold(holdId, key, HoldAllocationType.WL_RESERVATION,
                    null, null, "WL", tentativeWl, expiresAt);
        }

        throw new QuotaEligibilityException("Regret / Not Available: CNF, RAC, and Waitlist limits reached for " + key.asString());
    }

    private void rollbackSingleHoldLocked(JourneyInstance journey, QuotaPool pool, SeatHold hold) {
        if (!hold.deactivate()) {
            return;
        }
        switch (hold.getAllocationType()) {
            case CNF_HOLD -> {
                Berth b = findBerth(journey, hold.getPartitionKey().coachClass(), hold.getCoachCode(), hold.getBerthNumber());
                b.releaseHold(hold.getHoldId(), false);
                pool.releaseCnfHold();
            }
            case RAC_HOLD -> {
                Berth b = findBerth(journey, hold.getPartitionKey().coachClass(), hold.getCoachCode(), hold.getBerthNumber());
                b.releaseHold(hold.getHoldId(), true);
                pool.releaseRacHold();
            }
            case WL_RESERVATION -> pool.releaseWaitlistHold();
        }
    }

    private void reapExpiredHoldsLocked(JourneyInstance journey, PartitionKey key) {
        Queue<SeatHold> queue = partitionHoldIndex.get(key);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        QuotaPool pool = journey.getQuotaPool(key);
        Instant now = timeProvider.now();
        while (!queue.isEmpty()) {
            SeatHold head = queue.peek();
            if (!head.isActive()) {
                queue.poll();
                continue;
            }
            if (head.isExpired(now)) {
                queue.poll();
                rollbackSingleHoldLocked(journey, pool, head);
                activeHolds.invalidate(head.getHoldId());
            } else {
                break;
            }
        }
    }

    private Money baseFarePerPassenger(Train train, CoachClass coachClass) {
        double computed = Math.max(250.0, train.distanceKm() * coachClass.getRatePerKm());
        return Money.of(Math.round(computed));
    }

    private JourneyInstance requireJourney(String trainNumber, LocalDate journeyDate) {
        JourneyInstance journey = dataLoader.getJourney(trainNumber, journeyDate);
        if (journey == null) {
            throw new ResourceNotFoundException("No journey scheduled for train " + trainNumber + " on " + journeyDate);
        }
        return journey;
    }

    private QuotaPool requirePool(JourneyInstance journey, PartitionKey key) {
        QuotaPool pool = journey.getQuotaPool(key);
        if (pool == null) {
            throw new QuotaEligibilityException("Class " + key.coachClass().getCode() + " is not available on train " + key.trainNumber());
        }
        return pool;
    }

    private Berth findBerth(JourneyInstance journey, CoachClass coachClass, String coachCode, int berthNumber) {
        return journey.getCoaches(coachClass).stream()
                .filter(c -> c.getCoachCode().equals(coachCode))
                .flatMap(c -> c.findBerth(berthNumber).stream())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Berth not found: " + coachCode + "-" + berthNumber));
    }
}
