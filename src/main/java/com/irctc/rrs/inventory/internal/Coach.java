package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base coach demonstrating OOP Inheritance & Encapsulation.
 */
public abstract class Coach {

    private final String coachCode;
    private final CoachClass coachClass;
    private final List<Berth> berths;

    protected Coach(String coachCode, CoachClass coachClass, List<Berth> berths) {
        this.coachCode = coachCode;
        this.coachClass = coachClass;
        this.berths = List.copyOf(berths);
    }

    public String getCoachCode() {
        return coachCode;
    }

    public CoachClass getCoachClass() {
        return coachClass;
    }

    public List<Berth> getBerths() {
        return Collections.unmodifiableList(berths);
    }

    public abstract String berthLayoutRule();

    public Optional<Berth> findAvailableCnfBerth(BerthPreference preference, Instant now) {
        Optional<Berth> preferred = berths.stream()
                .filter(b -> b.isAvailableForCnf(now) && b.getBerthType().matchesPreference(preference))
                .findFirst();
        if (preferred.isPresent()) {
            return preferred;
        }
        return berths.stream().filter(b -> b.isAvailableForCnf(now)).findFirst();
    }

    public Optional<Berth> findAvailableRacBerth(Instant now) {
        return berths.stream().filter(b -> b.isAvailableForRac(now)).findFirst();
    }

    public Optional<Berth> findBerth(int berthNumber) {
        return berths.stream().filter(b -> b.getBerthNumber() == berthNumber).findFirst();
    }
}
