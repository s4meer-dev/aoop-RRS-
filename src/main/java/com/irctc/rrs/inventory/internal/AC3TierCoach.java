package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.CoachClass;

public class AC3TierCoach extends Coach {

    public AC3TierCoach(String coachCode, int totalBerths, int maxRacSideLowerBerths) {
        super(coachCode, CoachClass.AC_3, SleeperCoach.buildBerths(coachCode, totalBerths, maxRacSideLowerBerths));
    }

    @Override
    public String berthLayoutRule() {
        return "AC 3-Tier 8-Berth Cycle with RAC Side-Lower sharing";
    }
}
