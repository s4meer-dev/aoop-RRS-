package com.irctc.rrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the IRCTC-style Railway Reservation System.
 * Organizes the domain into Spring Modulith Bounded Contexts:
 * common, inventory, booking, waitlist, and payment.
 */
@SpringBootApplication
@EnableScheduling
public class RailwayReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailwayReservationApplication.class, args);
    }
}
