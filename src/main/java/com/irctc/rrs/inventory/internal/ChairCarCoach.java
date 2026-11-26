package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.BerthType;
import com.irctc.rrs.common.model.CoachClass;

import java.util.ArrayList;
import java.util.List;

public class ChairCarCoach extends Coach {

    public ChairCarCoach(String coachCode, int totalSeats) {
        super(coachCode, CoachClass.CHAIR_CAR, buildSeats(coachCode, totalSeats));
    }

    @Override
    public String berthLayoutRule() {
        return "5-Seat Chair Car (WS/AS/AS/WS/AS) - Confirmed Only";
    }

    private static List<Berth> buildSeats(String coachCode, int totalSeats) {
        List<Berth> list = new ArrayList<>(totalSeats);
        for (int num = 1; num <= totalSeats; num++) {
            int pos = (num - 1) % 5;
            BerthType type = (pos == 0 || pos == 3) ? BerthType.WINDOW : BerthType.AISLE;
            list.add(new Berth(coachCode, num, type, false));
        }
        return list;
    }
}
