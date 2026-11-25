# `src/payment` — Fault-Tolerant Payment Bounded Context (Unit 5 & Unit 7)

> **Status:** Planned module placeholder (Phase 4).

## Planned Components
- **Idempotency Safeguard:**
  - `IdempotencyLedger` storing unique `Idempotency-Key` records so duplicate clicks or network retries return cached outcomes without double charging.
- **Gateway Abstraction (`Abstraction`):**
  - `PaymentGateway` interface + `SimulatedBankGateway` (supports configurable latency & timeout injection for testing).
- **Timeout Reconciliation:**
  - `PaymentReconciliationWorker` polling `TIMEOUT_PENDING` records and resolving them cleanly to `RECONCILED_SUCCESS` or `RECONCILED_FAILED`.
- **Saga Compensation:**
  - `RefundCompensationService` issuing automatic, idempotent refunds when:
    1. Payment succeeds *after* Seat Hold TTL expired.
    2. Passenger cancels a ticket.
    3. Final Chart preparation drops remaining `WL` tickets.
