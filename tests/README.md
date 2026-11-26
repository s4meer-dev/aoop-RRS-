# Verification & Load Testing (`src/test/` & `tests/load/`)

## Automated Java Test Suites (`src/test/java/com/irctc/rrs/`)

| Test Class | Scope | Invariants Verified |
| :--- | :--- | :--- |
| `ModularityVerificationTests` | Spring Modulith (U7) | Enforces acyclic bounded-context isolation across `common`, `inventory`, `waitlist`, `payment`, `booking` |
| `QuotaStrategyAndBuilderTests` | Builder & Strategy (Slide 4) | Route/passenger validation, 10 AM AC / 11 AM Non-AC Tatkal clock gates, Ladies & Senior Citizen rules |
| `TicketStateAndWaitlistCascadeTests` | State & Cascade (Slide 6) | Legal/illegal transitions, atomic `CNF -> RAC -> WL` upgrades with zero gaps, charting auto-refunds |
| `FaultTolerantPaymentTests` | Payment Resilience (U5) | Idempotency-Key deduplication, timeout reconciliation to CNF, and Saga auto-refund on expired hold |
| `TatkalConcurrencyAndBackpressureTests` | Surge & Rate Limit (U6) | Concurrent burst produces 0 double-bookings, contiguous WL invariant, bounded `503` shedding & `429` limits |
| `ReservationApiIntegrationTests` | REST API & Actuator | Full HTTP booking, `Idempotency-Key` replay, and `/actuator/health` status check |

## External Load Testing (`tests/load/`)
- [`tatkal-surge.k6.js`](load/tatkal-surge.k6.js) — **Grafana k6** script simulating a 10:00 AM Tatkal spike (`0 -> 300 VUs`) with `Idempotency-Key` headers and zero `500` assertions.
