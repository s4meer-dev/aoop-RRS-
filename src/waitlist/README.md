# `src/waitlist` — Waitlist & RAC Bounded Context (`com.irctc.rrs.waitlist`)

Strictly encapsulated **Spring Modulith** module (`WaitlistService` public contract):

- **`ContiguousQueue`:** Guarantees strictly contiguous `1..N` indexing on every enqueue, promotion, or cancellation for both `RAC` and `WL`.
- **Atomic Cascading Upgrades (`WaitlistServiceImpl`):**
  - `CNF` cancel → promotes `RAC #1 -> CNF`, shifts `RAC` queue up by 1, promotes `WL #1 -> RAC`, shifts `WL` queue up by 1.
  - `RAC` cancel → shifts `RAC`, promotes `WL #1 -> RAC`, shifts `WL`.
  - `WL` cancel → removes entry and shifts `WL`.
- **`PromotionAuditEntry`:** Logs automated transitions for UI & verification.
