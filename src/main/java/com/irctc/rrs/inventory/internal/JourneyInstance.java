package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.QuotaCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Physical rake and quota pools for one train running on one specific date.
 */
public class JourneyInstance {

    private final Train train;
    private final LocalDate journeyDate;
    private final Map<CoachClass, List<Coach>> coachesByClass = new EnumMap<>(CoachClass.class);
    private final Map<PartitionKey, QuotaPool> quotaPools = new ConcurrentHashMap<>();
    private volatile boolean chartPrepared = false;
    private volatile Instant chartPreparedAt;

    public JourneyInstance(
            Train train,
            LocalDate journeyDate,
            int coachesPerClass,
            int berthsPerCoach,
            int racSlotsPerCoach,
            int maxWaitlistLimit
    ) {
        this.train = train;
        this.journeyDate = journeyDate;
        initializeRakeAndPools(coachesPerClass, berthsPerCoach, racSlotsPerCoach, maxWaitlistLimit);
    }

    private void initializeRakeAndPools(
            int coachesPerClass,
            int berthsPerCoach,
            int racSlotsPerCoach,
            int maxWaitlistLimit
    ) {
        int racBerthsPerCoach = Math.max(0, racSlotsPerCoach / 2);
        for (CoachClass coachClass : train.classes()) {
            List<Coach> coaches = new ArrayList<>();
            int prefixIndex = 1;
            int totalPhysicalCnf = 0;
            int totalPhysicalRacSlots = 0;

            for (int i = 0; i < coachesPerClass; i++) {
                String coachCode = coachPrefix(coachClass) + (prefixIndex++);
                Coach coach = switch (coachClass) {
                    case SLEEPER -> new SleeperCoach(coachCode, berthsPerCoach, racBerthsPerCoach);
                    case AC_3 -> new AC3TierCoach(coachCode, berthsPerCoach, racBerthsPerCoach);
                    case AC_2 -> new AC2TierCoach(coachCode, berthsPerCoach, racBerthsPerCoach);
                    case AC_1 -> new AC2TierCoach(coachCode, berthsPerCoach, 0);
                    case CHAIR_CAR -> new ChairCarCoach(coachCode, berthsPerCoach);
                };
                coaches.add(coach);

                long cnfInCoach = coach.getBerths().stream().filter(b -> !b.isRacShareable()).count();
                long racBerthsInCoach = coach.getBerths().stream().filter(Berth::isRacShareable).count();
                totalPhysicalCnf += (int) cnfInCoach;
                totalPhysicalRacSlots += (int) racBerthsInCoach * 2;
            }
            coachesByClass.put(coachClass, coaches);

            int gnCnf = Math.max(1, (int) Math.round(totalPhysicalCnf * 0.55));
            int tqCnf = Math.max(1, (int) Math.round(totalPhysicalCnf * 0.25));
            int ldCnf = Math.max(1, (totalPhysicalCnf - gnCnf - tqCnf) / 2);
            int ssCnf = Math.max(0, totalPhysicalCnf - gnCnf - tqCnf - ldCnf);

            quotaPools.put(
                    new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.GN),
                    new QuotaPool(new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.GN),
                            gnCnf, totalPhysicalRacSlots, maxWaitlistLimit)
            );
            quotaPools.put(
                    new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.TQ),
                    new QuotaPool(new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.TQ),
                            tqCnf, 0, Math.max(5, maxWaitlistLimit / 2))
            );
            quotaPools.put(
                    new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.LD),
                    new QuotaPool(new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.LD),
                            ldCnf, 0, Math.max(5, maxWaitlistLimit / 3))
            );
            quotaPools.put(
                    new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.SS),
                    new QuotaPool(new PartitionKey(train.number(), journeyDate, coachClass, QuotaCode.SS),
                            ssCnf, 0, Math.max(5, maxWaitlistLimit / 3))
            );
        }
    }

    private String coachPrefix(CoachClass coachClass) {
        return switch (coachClass) {
            case AC_1 -> "H";
            case AC_2 -> "A";
            case AC_3 -> "B";
            case SLEEPER -> "S";
            case CHAIR_CAR -> "C";
        };
    }

    public Train getTrain() {
        return train;
    }

    public LocalDate getJourneyDate() {
        return journeyDate;
    }

    public List<Coach> getCoaches(CoachClass coachClass) {
        return coachesByClass.getOrDefault(coachClass, List.of());
    }

    public Map<CoachClass, List<Coach>> getAllCoachesByClass() {
        return coachesByClass;
    }

    public QuotaPool getQuotaPool(PartitionKey key) {
        return quotaPools.get(key);
    }

    public Map<PartitionKey, QuotaPool> getQuotaPools() {
        return quotaPools;
    }

    public boolean isChartPrepared() {
        return chartPrepared;
    }

    public Instant getChartPreparedAt() {
        return chartPreparedAt;
    }

    public void markChartPrepared(Instant timestamp) {
        this.chartPrepared = true;
        this.chartPreparedAt = timestamp;
    }
}
