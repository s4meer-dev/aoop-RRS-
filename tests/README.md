# Planned Verification & Test Strategy (`tests/`)

> **Status:** Test strategy defined in Phase 0; automated test suites will be implemented alongside each phase in `src/`.

## Planned Test Suites

| Suite Folder | Target Focus | Key Invariants Verified |
| :--- | :--- | :--- |
| `tests/unit/` | Domain entities, `Builder`, `QuotaStrategy`, `TicketState` transitions | Quota eligibility rules, illegal state transition rejection, fare calculations |
| `tests/waitlist/` | Contiguous `WL -> RAC -> CNF` upgrade engine | Zero gaps (`1..N`) after arbitrary combinations of CNF, RAC & WL cancellations |
| `tests/payment/` | Unit 5 fault-tolerant payment flows | Same `Idempotency-Key` charges once; timeout reconciliation resolves cleanly; late success triggers auto-refund |
| `tests/concurrency/` | Unit 6 10 AM Tatkal rush simulation | 1,000+ concurrent booking threads produce **0 double-bookings** and graceful bounded-queue shedding |
| `tests/integration/` | End-to-end Booking → Inventory → Waitlist → Payment → Charting | Full journey simulation from Tatkal opening to pre-departure chart preparation |
