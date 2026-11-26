# `src/inventory` — Inventory Bounded Context (`com.irctc.rrs.inventory`)

Strictly encapsulated **Spring Modulith** module (`InventoryService` public contract; domain & strategies hidden under `internal`):

- **Entities (`Encapsulation` & `Inheritance`):** `Train`, `JourneyInstance`, abstract `Coach` (`SleeperCoach`, `AC3TierCoach`, `AC2TierCoach`, `ChairCarCoach`), `Berth`, `QuotaPool`, and `SeatHold` (managed with **Caffeine** `180s` TTL expiry).
- **Seed Data (`datameet/railways`):** `TrainDataLoader` loads real Indian Railways trains from `src/main/resources/data/indian-railways-trains.json`.
- **Quota Strategies (`Polymorphism` & `Strategy Pattern`):** `QuotaStrategy` implemented by `GeneralQuotaStrategy` (`GN`), `TatkalQuotaStrategy` (`TQ`), `LadiesQuotaStrategy` (`LD`), and `SeniorCitizenQuotaStrategy` (`SS`).
