package com.irctc.rrs.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Deterministic time abstraction enabling repeatable testing of 10 AM/11 AM Tatkal windows and Hold TTLs.
 */
public interface TimeProvider {

    Instant now();

    LocalDate currentDate();

    LocalTime currentTime();
}
