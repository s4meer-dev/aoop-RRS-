# Source Code Skeleton (`src/`)

> **Status:** Architectural placeholder structure only. Implementation starts in **Phase 1** after design sign-off.

This directory organizes the codebase around **Unit 7 — Bounded Contexts** plus a shared kernel (`common`), keeping every service decoupled and strictly respecting OOP boundaries.

| Directory | Bounded Context / Role | Primary Responsibility |
| :--- | :--- | :--- |
| [`common/`](common/README.md) | Shared Kernel | Value objects (`PNR`, `Money`), custom exceptions, clock/ID abstractions & partition lock primitives |
| [`inventory/`](inventory/README.md) | Inventory Context (U7) | Trains, `Coach`/`Berth` hierarchy, `QuotaStrategy` implementations, TTL seat holds & chart preparation |
| [`booking/`](booking/README.md) | Booking Context (U7 + U6) | U6 Backpressure Gate (bounded queue + throttling), `BookingRequestBuilder` & `TicketState` machine |
| [`waitlist/`](waitlist/README.md) | Waitlist Context (U7) | Contiguous `RAC` & `WL` queues and atomic gap-free cascade promotion engine |
| [`payment/`](payment/README.md) | Payment Context (U7 + U5) | Idempotency ledger, gateway adapter, timeout reconciler & automated refund compensation |
