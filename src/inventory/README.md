# `src/inventory` — Inventory Bounded Context (Unit 7)

> **Status:** Planned module placeholder (Phase 1 & Phase 3).

## Planned Components
- **Domain Entities (`Encapsulation` & `Inheritance`):**
  - `Train`, `JourneyInstance`
  - Abstract `Coach` → `SleeperCoach`, `AC3TierCoach`, `AC2TierCoach`, `ChairCarCoach`
  - `Berth` (regular vs. RAC-shareable Side-Lower)
  - `QuotaPool` & `SeatHold` (temporary lock with TTL expiry)
- **Quota & Fare Strategies (`Polymorphism` & `Strategy Pattern`):**
  - `QuotaStrategy` interface
  - `GeneralQuotaStrategy` (`GN`)
  - `TatkalQuotaStrategy` (`TQ` — 10 AM AC / 11 AM Non-AC window + dynamic surcharge)
  - `LadiesQuotaStrategy` (`LD`)
  - `SeniorCitizenQuotaStrategy` (`SS`)
- **Charting Service:**
  - `ChartPreparationService` (freezes online bookings at `T - 4h` and publishes final passenger berth list).
