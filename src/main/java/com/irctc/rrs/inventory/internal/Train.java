package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.model.CoachClass;

import java.util.List;

public record Train(
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
) {
}
