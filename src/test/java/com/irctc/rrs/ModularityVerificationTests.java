package com.irctc.rrs;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Unit 7 Bounded Context isolation across common, inventory, booking, waitlist, and payment.
 */
class ModularityVerificationTests {

    @Test
    void boundedContextsRespectStrictDependencyBoundaries() {
        ApplicationModules modules = ApplicationModules.of(RailwayReservationApplication.class);
        modules.verify();

        assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("common", "inventory", "waitlist", "payment", "booking");
    }
}
