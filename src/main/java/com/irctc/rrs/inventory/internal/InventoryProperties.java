package com.irctc.rrs.inventory.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rrs.inventory")
public class InventoryProperties {

    private int seatHoldTtlSeconds = 180;
    private boolean enforceTatkalWindow = false;
    private int coachesPerClass = 1;
    private int berthsPerCoach = 24;
    private int racSideLowerSlotsPerCoach = 4;
    private int maxWaitlistLimit = 20;

    public int getSeatHoldTtlSeconds() {
        return seatHoldTtlSeconds;
    }

    public void setSeatHoldTtlSeconds(int seatHoldTtlSeconds) {
        this.seatHoldTtlSeconds = seatHoldTtlSeconds;
    }

    public boolean isEnforceTatkalWindow() {
        return enforceTatkalWindow;
    }

    public void setEnforceTatkalWindow(boolean enforceTatkalWindow) {
        this.enforceTatkalWindow = enforceTatkalWindow;
    }

    public int getCoachesPerClass() {
        return coachesPerClass;
    }

    public void setCoachesPerClass(int coachesPerClass) {
        this.coachesPerClass = coachesPerClass;
    }

    public int getBerthsPerCoach() {
        return berthsPerCoach;
    }

    public void setBerthsPerCoach(int berthsPerCoach) {
        this.berthsPerCoach = berthsPerCoach;
    }

    public int getRacSideLowerSlotsPerCoach() {
        return racSideLowerSlotsPerCoach;
    }

    public void setRacSideLowerSlotsPerCoach(int racSideLowerSlotsPerCoach) {
        this.racSideLowerSlotsPerCoach = racSideLowerSlotsPerCoach;
    }

    public int getMaxWaitlistLimit() {
        return maxWaitlistLimit;
    }

    public void setMaxWaitlistLimit(int maxWaitlistLimit) {
        this.maxWaitlistLimit = maxWaitlistLimit;
    }
}
