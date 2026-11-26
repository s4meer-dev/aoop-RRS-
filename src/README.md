# Source Code Architecture (`src/`)

Implemented under standard Maven layout (`src/main/java/com/irctc/rrs/`) with strict **Spring Modulith** bounded-context verification:

| Package | Bounded Context / Role | Public Contract | Blueprint Reference |
| :--- | :--- | :--- | :--- |
| `com.irctc.rrs.common` | Shared Kernel (`OPEN` module) | `Money`, `PartitionKey`, `PartitionLockManager`, `TimeProvider`, events & exceptions | [`common/README.md`](common/README.md) |
| `com.irctc.rrs.inventory` | Inventory Context (U7) | `InventoryService` (Trains, `Coach`/`Berth`, `QuotaStrategy`, Caffeine holds, charting) | [`inventory/README.md`](inventory/README.md) |
| `com.irctc.rrs.waitlist` | Waitlist Context (U7) | `WaitlistService` (Contiguous `RAC`/`WL` queues & gap-free cascade upgrades) | [`waitlist/README.md`](waitlist/README.md) |
| `com.irctc.rrs.payment` | Payment Context (U7 + U5) | `PaymentService` (`IdempotencyLedger`, `SimulatedBankGateway`, reconciler, Saga refunds) | [`payment/README.md`](payment/README.md) |
| `com.irctc.rrs.booking` | Booking Context (U7 + U6) | `BookingService`, `BookingRequestBuilder`, `TicketState`, `TatkalAdmissionGate` & REST API | [`booking/README.md`](booking/README.md) |
