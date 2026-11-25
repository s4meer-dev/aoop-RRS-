# `src/waitlist` — Waitlist & RAC Bounded Context (Unit 7)

> **Status:** Planned module placeholder (Phase 2).

## Planned Components
- **Contiguous Queue Data Structures:**
  - `ContiguousQueue` maintaining zero-gap indices `1..N` for `RAC` and `WL` per `(Train, Date, Class, Quota)`.
  - `WaitlistEntry` linking `ticketId`, `queueType` (`RAC` or `WL`), and `contiguousPosition`.
- **Cascading Upgrade Engine (Slide 6):**
  - `WaitlistUpgradeService`:
    - On `CNF` cancellation → promotes `RAC #1` to `CNF`, shifts `RAC` queue up by 1, promotes `WL #1` to `RAC`, shifts `WL` queue up by 1.
    - On `RAC` cancellation → removes slot, shifts `RAC` queue up by 1, promotes `WL #1` to `RAC`, shifts `WL` queue up by 1.
    - On `WL` cancellation → removes entry and shifts `WL` queue up by 1.
- **Audit Trail:**
  - `PromotionAuditLog` recording every automated upgrade for PNR status transparency.
