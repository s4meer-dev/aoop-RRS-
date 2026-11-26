@org.springframework.modulith.ApplicationModule(
        displayName = "Booking Bounded Context",
        allowedDependencies = {"common", "inventory", "waitlist", "payment"}
)
package com.irctc.rrs.booking;
