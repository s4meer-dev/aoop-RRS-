package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.BerthType;
import com.irctc.rrs.common.model.CoachClass;

import java.util.ArrayList;
import java.util.List;

public class SleeperCoach extends Coach {

    public SleeperCoach(String coachCode, int totalBerths, int maxRacSideLowerBerths) {
        super(coachCode, CoachClass.SLEEPER, buildBerths(coachCode, totalBerths, maxRacSideLowerBerths));
    }

    @Override
    public String berthLayoutRule() {
        return "8-Berth Bay Cycle (LB/MB/UB/LB/MB/UB/SL/SU) with RAC Side-Lower sharing";
    }

    static List<Berth> buildBerths(String coachCode, int totalBerths, int maxRacSideLowerBerths) {
        List<Berth> list = new ArrayList<>(totalBerths);
        int racAssigned = 0;
        for (int num = 1; num <= totalBerths; num++) {
            int pos = (num - 1) % 8;
            BerthType type = switch (pos) {
                case 0, 3 -> BerthType.LOWER;
                case 1, 4 -> BerthType.MIDDLE;
                case 2, 5 -> BerthType.UPPER;
                case 6 -> BerthType.SIDE_LOWER;
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
