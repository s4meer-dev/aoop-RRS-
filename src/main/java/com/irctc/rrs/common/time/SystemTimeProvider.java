package com.irctc.rrs.common.time;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class SystemTimeProvider implements TimeProvider {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDate currentDate() {
        return LocalDate.now(IST_ZONE);
    }

    @Override
    public LocalTime currentTime() {
        return LocalTime.now(IST_ZONE);
    }
}
