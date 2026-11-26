# `src/payment` — Fault-Tolerant Payment Context (`com.irctc.rrs.payment`)

Strictly encapsulated **Spring Modulith** module implementing Unit 5 safeguards (`PaymentService` public contract):

- **Idempotency Ledger (`PaymentServiceImpl`):** Deduplicates retries using `Idempotency-Key` and guards against amount mismatches (`PaymentConflictException`).
- **Gateway Resilience (`SimulatedBankGateway`):** Protected by `Resilience4j` `Retry` + `CircuitBreaker`, supporting deterministic timeout & late-settlement simulation modes.
- **Timeout Reconciliation:** Scheduled & on-demand reconciler resolving `TIMEOUT_PENDING` payments and publishing `PaymentReconciledEvent`.
- **Saga Compensation:** Idempotent automatic refunds triggered on late post-expiry bank debits, cancellations, and chart drops.
