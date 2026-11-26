# `src/common` — Shared Kernel (`com.irctc.rrs.common`)

Exposed as an `OPEN` Spring Modulith shared kernel across all bounded contexts:

- **Value Objects (`common.model`):** `Money`, `PartitionKey`, `CoachClass`, `QuotaCode`, `BerthType`, `BerthPreference`, `Gender`, `TicketStatus`.
- **Concurrency (`common.concurrency`):** `PartitionLockManager` providing fair per-`(Train, Date, Class, Quota)` `ReentrantLock` execution.
- **Abstractions (`common.time`, `common.id`):** `TimeProvider` (`SystemTimeProvider`) and `IdGenerator` (`DefaultIdGenerator`).
- **Events & Exceptions (`common.events`, `common.exception`):** `BerthFreedEvent`, `TicketUpgradedEvent`, `PaymentReconciledEvent`, `RefundTriggeredEvent`, and structured `RrsException` subclasses (`400`, `404`, `409`, `429`, `503`).
