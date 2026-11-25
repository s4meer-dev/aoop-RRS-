# `src/common` — Shared Kernel

> **Status:** Planned module placeholder (Phase 1).

## Planned Components
- **Value Objects:** `PnrNumber`, `TrainNumber`, `StationCode`, `Money`, `CoachClass`, `QuotaCode`, `BerthPreference`.
- **Concurrency Utilities:** `PartitionKey` `(Train, Date, Class, Quota)` and `PartitionLockManager` interface.
- **Domain Events & Exceptions:** Base `DomainEvent`, `QuotaEligibilityException`, `SeatHoldExpiredException`, `SystemBusyException`.
- **Clock & ID Providers:** Testable `TimeProvider` and `IdGenerator` abstractions so Tatkal 10 AM windows and hold TTLs can be tested deterministically.
