# 08 — Integrated Open-Source Ecosystem & Engineering References

> **In simple words:** Instead of reinventing basic plumbing from scratch, our IRCTC-style engine integrates battle-tested open-source libraries and architectural patterns from top GitHub projects into one cohesive, production-grade Java system.

---

## Integrated Open-Source Libraries & Datasets

| Reference Repository | Role in Our Project | Where Integrated |
| :--- | :--- | :--- |
| [spring-projects/spring-modulith](https://github.com/spring-projects/spring-modulith) | Programmatically verifies **Unit 7 Bounded Contexts** (`common`, `inventory`, `waitlist`, `payment`, `booking`) with zero illegal cross-module dependencies | `package-info.java` per module + `ModularityVerificationTests` |
| [datameet/railways](https://github.com/datameet/railways) | Real Indian Railways train numbers, station codes (`BCT`, `NDLS`, `HWH`, `MAS`, `SBC`), distances & durations (`12951`, `12301`, `12002`, etc.) | `src/main/resources/data/indian-railways-trains.json` + `TrainDataLoader` |
| [bucket4j/bucket4j](https://github.com/bucket4j/bucket4j) | Per-user token-bucket rate limiting (`HTTP 429`) stopping single-bot bursts at 10 AM Tatkal | `TatkalAdmissionGate` (`src/main/java/com/irctc/rrs/booking/internal/TatkalAdmissionGate.java`) |
| [resilience4j/resilience4j](https://github.com/resilience4j/resilience4j) | `Bulkhead` concurrency caps around booking execution + `Retry` & `CircuitBreaker` protecting bank gateway calls | `TatkalAdmissionGate` + `SimulatedBankGateway` |
| [LMAX-Exchange/disruptor](https://github.com/LMAX-Exchange/disruptor) | Lock-free ring buffer capturing admission, shedding, payment timeout, and upgrade telemetry | `BookingTelemetryDisruptor` |
| [ben-manes/caffeine](https://github.com/ben-manes/caffeine) | High-speed `180s` TTL cache managing temporary Seat Holds before payment | `InventoryServiceImpl` |
| [micrometer-metrics/micrometer](https://github.com/micrometer-metrics/micrometer) | Exposes Prometheus & Actuator counters/gauges (`rrs.admission.queue.depth`, `rrs.bookings.*`, `rrs.payments.*`) | `TatkalAdmissionGate`, `BookingOrchestrator`, `PaymentServiceImpl`, `monitoring/prometheus.yml` |
| [grafana/k6](https://github.com/grafana/k6) | Load-testing script simulating a 10:00 AM Tatkal ramp-up & spike (`0 -> 300 VUs`) | `tests/load/tatkal-surge.k6.js` |

---

## Architectural Blueprints Adopted

- **[nageoffer/12306](https://github.com/nageoffer/12306) & [zaiyunduan123/springboot-seckill](https://github.com/zaiyunduan123/springboot-seckill):** Inspired partitioned seat locking, microsecond hold scopes, and bounded queue backpressure during 10 AM booking rushes.
- **[iluwatar/java-design-patterns](https://github.com/iluwatar/java-design-patterns) & [ashishps1/awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design):** Guided clean `Builder` (`BookingRequestBuilder`), `Strategy` (`QuotaStrategy`), and `State` (`TicketState`) OOP implementations.
- **[stripe/stripe-java](https://github.com/stripe/stripe-java) & [eventuate-tram/eventuate-tram-sagas](https://github.com/eventuate-tram/eventuate-tram-sagas):** Guided `Idempotency-Key` ledger deduplication, `TIMEOUT_PENDING` bank reconciliation, and compensating refund Sagas.
- **[vedhasreeyuvaraj887/Concert-Booking](https://github.com/vedhasreeyuvaraj887/Concert-Booking):** Reinforced TTL seat-hold expiration + automatic waiting-queue promotion invariants.
