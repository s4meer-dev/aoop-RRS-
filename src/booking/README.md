# `src/booking` — Booking Context & Tatkal Backpressure Gate (`com.irctc.rrs.booking`)

Strictly encapsulated **Spring Modulith** orchestrating context (`BookingService` & `BookingRequestBuilder` public; internals hidden under `internal`):

- **Unit 6 Admission Gate (`TatkalAdmissionGate`):** `Bucket4j` per-user token buckets (`429`) + bounded `ArrayBlockingQueue` & fixed worker pool (`503 Retry-After`) + `Resilience4j Bulkhead` + `LMAX Disruptor` telemetry (`BookingTelemetryDisruptor`).
- **Builder Pattern:** `BookingRequestBuilder` assembling validated immutable `BookingRequest`s.
- **State Pattern (`booking.internal.state`):** `Ticket` delegating transitions to `InitiatedState`, `PaymentPendingState`, `WaitlistedState`, `RacState`, `ConfirmedState`, `ChartedState`, `CancelledState`, and `ExpiredState`.
- **Orchestrator & Web Layer:** `BookingOrchestrator`, `ReservationController` (`/api/v1/**`), and `GlobalExceptionHandler`.
