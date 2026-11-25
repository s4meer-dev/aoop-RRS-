# `src/booking` — Booking Bounded Context (Unit 7) & Backpressure Gate (Unit 6)

> **Status:** Planned module placeholder (Phase 1–3).

## Planned Components
- **10 AM Rush Backpressure (`Unit 6`):**
  - `TatkalAdmissionGate`: Rate limiter + fixed-capacity **Bounded Queue** + throttled worker thread pool.
  - Sheds extreme overflow gracefully (`SystemBusyException` / `503 Retry-After`) without crashing the JVM/process.
- **Request Assembly (`Builder Pattern`):**
  - `BookingRequest` (immutable validated input)
  - `BookingRequestBuilder` (fluent step-by-step builder enforcing 1–6 passenger limit and route/age validation).
- **Ticket Lifecycle (`State Pattern`):**
  - `Ticket` & `BookingPnr` aggregates
  - `TicketState` interface → `InitiatedState`, `PaymentPendingState`, `WaitlistedState`, `RacState`, `ConfirmedState`, `ChartedState`, `CancelledState`, `ExpiredState`.
- **Orchestration (`Abstraction`):**
  - `BookingOrchestrator` coordinating Hold → Payment → Commit/Waitlist → Cancellation without leaking internal locks or gateway details.
