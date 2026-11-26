package com.irctc.rrs.common.model;

/**
 * Physical berth and seat types inside railway coaches.
 */
public enum BerthType {
    LOWER("LB"),
    MIDDLE("MB"),
    UPPER("UB"),
    SIDE_LOWER("SL"),
    SIDE_UPPER("SU"),
    WINDOW("WS"),
    AISLE("AS");

    private final String shortCode;

    BerthType(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }

    public boolean matchesPreference(BerthPreference preference) {
        if (preference == null || preference == BerthPreference.NONE) {
            return true;
        }
        return switch (preference) {
            case LOWER -> this == LOWER;
            case MIDDLE -> this == MIDDLE;
            case UPPER -> this == UPPER;
            case SIDE_LOWER -> this == SIDE_LOWER;
            case SIDE_UPPER -> this == SIDE_UPPER;
            case WINDOW -> this == WINDOW;
            case NONE -> true;
        };
    }
}
