package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.BerthType;
import com.irctc.rrs.common.model.CoachClass;

import java.util.ArrayList;
import java.util.List;

public class AC2TierCoach extends Coach {

    public AC2TierCoach(String coachCode, int totalBerths, int maxRacSideLowerBerths) {
        super(coachCode, CoachClass.AC_2, buildBerths(coachCode, totalBerths, maxRacSideLowerBerths));
    }

    @Override
    public String berthLayoutRule() {
        return "AC 2-Tier 6-Berth Cycle (LB/UB/LB/UB/SL/SU) with RAC Side-Lower sharing";
    }

    private static List<Berth> buildBerths(String coachCode, int totalBerths, int maxRacSideLowerBerths) {
        List<Berth> list = new ArrayList<>(totalBerths);
        int racAssigned = 0;
        for (int num = 1; num <= totalBerths; num++) {
            int pos = (num - 1) % 6;
            BerthType type = switch (pos) {
                case 0, 2 -> BerthType.LOWER;
                case 1, 3 -> BerthType.UPPER;
                case 4 -> BerthType.SIDE_LOWER;
                default -> BerthType.SIDE_UPPER;
            };
            boolean racSlot = (type == BerthType.SIDE_LOWER && racAssigned < maxRacSideLowerBerths);
            if (racSlot) {
                racAssigned++;
            }
            list.add(new Berth(coachCode, num, type, racSlot));
        }
        return list;
    }
}
