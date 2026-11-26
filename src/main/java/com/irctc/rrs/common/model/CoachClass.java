package com.irctc.rrs.common.model;

/**
 * Train coach classes with base pricing and AC/RAC capability metadata.
 */
public enum CoachClass {
    AC_1("1A", true, false, 4.20),
    AC_2("2A", true, true, 2.75),
    AC_3("3A", true, true, 1.95),
    SLEEPER("SL", false, true, 0.75),
    CHAIR_CAR("CC", true, false, 1.45);

    private final String code;
    private final boolean airConditioned;
    private final boolean supportsRac;
    private final double ratePerKm;

    CoachClass(String code, boolean airConditioned, boolean supportsRac, double ratePerKm) {
        this.code = code;
        this.airConditioned = airConditioned;
        this.supportsRac = supportsRac;
        this.ratePerKm = ratePerKm;
    }

    public String getCode() {
        return code;
    }

    public boolean isAirConditioned() {
        return airConditioned;
    }

    public boolean supportsRac() {
        return supportsRac;
    }

    public double getRatePerKm() {
        return ratePerKm;
    }
}
