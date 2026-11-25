# 06 — Domain Data Model & Ownership Boundaries

> **In simple words:** Every piece of information in our railway system has one clear home. A `Train` holds `Coaches`, `Coaches` hold `Berths`, each `JourneyInstance` splits capacity into `QuotaPools`, and each of the **four Bounded Contexts (U7)** strictly owns only its own tables/entities.

---

## 1. Train → Coach → Berth → Quota Pool Hierarchy

A single physical train running on a specific date has coaches, physical berths inside each coach, and logical **Quota Pools** slicing those seats for General (`GN`), Tatkal (`TQ`), Ladies (`LD`), and Senior Citizen (`SS`) passengers.

```mermaid
flowchart TD
    Train["Train<br/>(12951 - Mumbai Rajdhani)"]
    Journey["JourneyInstance<br/>(Train 12951 on 15-Dec-2026)"]
    Class["CoachClass Pool<br/>(e.g., 3A / SL / 2A)"]

    Coach1["Coach B1<br/>(72 Berths)"]
    Coach2["Coach B2<br/>(72 Berths)"]

    BerthReg["Regular Berths<br/>(LB, MB, UB, SU)"]
    BerthSL["Side-Lower Berths<br/>(Eligible for 2-Passenger RAC Sharing)"]

    Q_GN["General Quota Pool (GN)<br/>CNF + RAC + WL Limits"]
    Q_TQ["Tatkal Quota Pool (TQ)<br/>10 AM / 11 AM Pool"]
    Q_LD["Ladies Quota Pool (LD)"]
    Q_SS["Senior Citizen Pool (SS)"]

    Train --> Journey
    Journey --> Class
    Class --> Coach1
    Class --> Coach2
    Coach1 --> BerthReg
    Coach1 --> BerthSL

    Class --> Q_GN
    Class --> Q_TQ
    Class --> Q_LD
    Class --> Q_SS
```

---

## 2. Conceptual Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    TRAIN ||--o{ JOURNEY_INSTANCE : "operates on date"
    JOURNEY_INSTANCE ||--|{ COACH : "has rake coaches"
    COACH ||--|{ BERTH : "contains"
    JOURNEY_INSTANCE ||--|{ QUOTA_POOL : "allocates seats into"

    BOOKING_PNR ||--|{ TICKET_ITEM : "contains 1..6 passengers"
    PASSENGER ||--o{ TICKET_ITEM : "booked as"
    QUOTA_POOL ||--o{ BOOKING_PNR : "governs quota"

    TICKET_ITEM ||--o| BERTH : "assigned when CNF or RAC"
    TICKET_ITEM ||--o| WAITLIST_ENTRY : "queued when RAC or WL"

    BOOKING_PNR ||--|| PAYMENT_TRANSACTION : "paid via idempotent txn"
    PAYMENT_TRANSACTION ||--o{ REFUND_RECORD : "compensated by"

    TRAIN {
        string train_number PK
        string train_name
        string source_code
        string destination_code
    }

    JOURNEY_INSTANCE {
        string journey_id PK
        string train_number FK
        date journey_date
        string chart_status
    }

    COACH {
        string coach_id PK
        string journey_id FK
        string coach_code
        string coach_class
    }

    BERTH {
        string berth_id PK
        string coach_id FK
        int berth_number
        string berth_type
        string occupancy_mode
    }

    QUOTA_POOL {
        string quota_pool_id PK
        string journey_id FK
        string coach_class
        string quota_code
        int total_cnf_slots
        int available_cnf_slots
        int total_rac_slots
        int available_rac_slots
        int max_wl_limit
        int current_wl_count
    }

    BOOKING_PNR {
        string pnr_number PK
        string quota_pool_id FK
        string booking_status
        decimal total_fare
        timestamp created_at
    }

    TICKET_ITEM {
        string ticket_id PK
        string pnr_number FK
        string passenger_id FK
        string current_state
        string assigned_berth_id FK
        int rac_number
        int wl_number
    }

    PASSENGER {
        string passenger_id PK
        string full_name
        int age
        string gender
        string berth_preference
    }

    WAITLIST_ENTRY {
        string entry_id PK
        string quota_pool_id FK
        string ticket_id FK
        string queue_type
        int contiguous_position
        timestamp enqueued_at
    }

    PAYMENT_TRANSACTION {
        string payment_id PK
        string pnr_number FK
        string idempotency_key UK
        decimal amount
        string payment_status
        string gateway_ref
        timestamp updated_at
    }

    REFUND_RECORD {
        string refund_id PK
        string payment_id FK
        decimal refund_amount
        string reason
        string refund_status
        timestamp processed_at
    }
```

---

## 3. Bounded Context Data Ownership (Unit 7 Rule)

To enforce strict isolation (**Unit 7: Bounded Contexts**), **no service may directly mutate another service's entities**. Cross-context references use immutable IDs (`pnr_number`, `quota_pool_id`, `payment_id`, `berth_id`).

```mermaid
flowchart LR
    subgraph InventoryOwner["Inventory Context Owns"]
        E1["TRAIN"]
        E2["JOURNEY_INSTANCE"]
        E3["COACH"]
        E4["BERTH"]
        E5["QUOTA_POOL"]
        E6["SEAT_HOLD (TTL)"]
    end

    subgraph BookingOwner["Booking Context Owns"]
        B1["BOOKING_PNR"]
        B2["TICKET_ITEM"]
        B3["PASSENGER"]
    end

    subgraph WaitlistOwner["Waitlist Context Owns"]
        W1["WAITLIST_ENTRY (RAC & WL Queues)"]
        W2["PROMOTION_AUDIT_LOG"]
    end

    subgraph PaymentOwner["Payment Context Owns (U5)"]
        P1["PAYMENT_TRANSACTION"]
        P2["IDEMPOTENCY_LEDGER"]
        P3["REFUND_RECORD"]
    end
```

---

## 4. Key Data Constraints & Invariants

| Entity | Invariant / Constraint | Reason |
| :--- | :--- | :--- |
| `BERTH` | `CNF` allows max `1` `TICKET_ITEM`; `RAC` (Side-Lower only) allows max `2` `TICKET_ITEM`s | Prevents physical double-booking while supporting IRCTC RAC berth sharing |
| `QUOTA_POOL` | `0 <= available_cnf_slots <= total_cnf_slots` | Guarantees seat counts never go negative under concurrency |
| `WAITLIST_ENTRY` | `(quota_pool_id, queue_type, contiguous_position)` is unique & gap-free `1..N` | Enforces Slide 2 & 6 contiguous waitlist/RAC requirement |
| `PAYMENT_TRANSACTION` | `idempotency_key` has a strict `UNIQUE` constraint | Enforces Unit 5 "never charge twice on retry" rule |
| `TICKET_ITEM` | Cannot hold both `wl_number > 0` and `assigned_berth_id != null` simultaneously | Prevents corrupt hybrid ticket states |

---

> **Next:** See [04 — Ticket Lifecycle & Contiguous Waitlist](04-ticket-lifecycle-and-waitlist.md) and [05 — Fault-Tolerant Payments](05-fault-tolerant-payments.md) for detailed behavioral flowcharts.
