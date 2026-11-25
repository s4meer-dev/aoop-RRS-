# 03 — Core Domain, OOP Pillars & Design Patterns

> **In simple words:** Instead of writing one tangled script full of `if-else` checks, we organize our railway system like real-world objects—a `Ticket`, a `Coach`, a `Berth`, a `QuotaPolicy`—each protecting its own rules and plugging together cleanly like Lego blocks.

---

## 1. How the Four OOP Pillars Map to Our Project (Slide 4)

```mermaid
flowchart TB
    OOP["Object-Oriented Core Domain"]

    OOP --> Enc["1. Encapsulation<br/>Protect critical data"]
    OOP --> Inh["2. Inheritance<br/>Reuse shared behavior"]
    OOP --> Poly["3. Polymorphism<br/>Plug in different quotas"]
    OOP --> Abs["4. Abstraction<br/>Hide complex internals"]

    Enc --> EncEx["Private seat counts, berth status,<br/>PNR state & payment ledger.<br/>Only changed via validated methods."]
    Inh --> InhEx["Base Coach / Base QuotaPolicy /<br/>Base DomainEvent shared across<br/>specialized subclasses."]
    Poly --> PolyEx["Same allocate() call behaves<br/>differently for General, Tatkal,<br/>Ladies & Senior Citizen quotas."]
    Abs --> AbsEx["BookingService hides locks,<br/>bounded queues, retries &<br/>bank gateway details."]
```

| OOP Pillar | PPT Definition | Concrete Application in Our System | Why It Matters |
| :--- | :--- | :--- | :--- |
| **Encapsulation** | *Protecting important booking data* | `Berth.status`, `QuotaPool.availableSeats`, `Ticket.currentState`, and `PaymentRecord.idempotencyKey` are `private`. State transitions only happen through guarded domain methods (e.g., `hold()`, `confirm()`, `cancel()`). | Prevents accidental negative seat counts, invalid status jumps (like `WL -> CHARTED`), or unvalidated berth edits. |
| **Inheritance** | *Reusing common features* | `BaseCoach` shares coach code, capacity, and berth lookup across `SleeperCoach`, `AC3TierCoach`, `AC2TierCoach`, `ChairCarCoach`. `BaseQuotaPolicy` shares common passenger/date validation across quota implementations. | Eliminates duplicated coach and validation logic across train classes. |
| **Polymorphism** | *Supporting different booking quotas* | `QuotaStrategy` interface defines `allocate()` and `calculateFare()`. `GeneralQuota`, `TatkalQuota`, `LadiesQuota`, and `SeniorCitizenQuota` each implement their own eligibility and timing rules. | Eliminates Slide 3's *"Hardcoded Quota Conditionals"*—adding a new quota requires zero changes to `BookingService`. |
| **Abstraction** | *Hiding unnecessary implementation details* | Callers interact with clean interfaces (`BookingService`, `PaymentGateway`, `WaitlistManager`) without knowing about `PartitionLock`, `BoundedQueue`, `IdempotencyLedger`, or gateway HTTP polling. | Keeps modules decoupled (Unit 7 Bounded Contexts) and easy to test independently. |

---

## 2. Core Domain Class Hierarchy (Inheritance & Encapsulation)

```mermaid
classDiagram
    class Coach {
        <<abstract>>
        -String coachId
        -CoachClass coachClass
        -List berths
        +getAvailableBerths() List
        +getRacBerths() List
        +findPreferredBerth(BerthPreference) Berth
    }

    class SleeperCoach {
        -int totalBerths
        -int sideLowerRacCount
        +berthLayoutRule() String
    }

    class AC3TierCoach {
        -int totalBerths
        -int sideLowerRacCount
        +berthLayoutRule() String
    }

    class AC2TierCoach {
        -int totalBerths
        -int sideLowerRacCount
        +berthLayoutRule() String
    }

    class Berth {
        -int berthNumber
        -BerthType type
        -BerthState occupancyState
        -String heldByBookingId
        +acquireHold(String bookingId, Instant expiresAt) boolean
        +commitToConfirmed(String pnr) void
        +addRacPassenger(String pnr) boolean
        +releaseSlot(String pnr) void
    }

    class Passenger {
        -String passengerId
        -String name
        -int age
        -Gender gender
        -BerthPreference preference
        +isSeniorCitizen() boolean
        +isEligibleForLadiesQuota() boolean
    }

    Coach <|-- SleeperCoach
    Coach <|-- AC3TierCoach
    Coach <|-- AC2TierCoach
    Coach "1" *-- "many" Berth : contains
```

---

## 3. Design Pattern 1 — Strategy Pattern (Quotas & Dynamic Pricing)

### Problem Solved
In legacy systems, quota logic is trapped inside giant `if-else` blocks:
- *General (`GN`)* allows booking up to 60–120 days ahead, standard fare, supports RAC & WL.
- *Tatkal (`TQ`)* opens strictly at `10:00 AM` (AC) / `11:00 AM` (Non-AC) one day before departure, charges dynamic Tatkal surcharge, enforces stricter cancellation refund rules.
- *Ladies (`LD`)* restricts allocation to eligible female/child passengers.
- *Senior Citizen (`SS`)* prioritizes Lower Berths for eligible elderly passengers.

### Solution with Strategy Pattern
> **In simple words:** The Booking Engine asks the selected `QuotaStrategy` *"Can this passenger book under your rules, and what is the fare?"* Each quota plugs in its own rulebook without changing the booking engine.

```mermaid
classDiagram
    class QuotaStrategy {
        <<interface>>
        +getQuotaCode() QuotaCode
        +validateEligibility(BookingRequest, Instant now) ValidationResult
        +selectCandidateSlot(QuotaPool, BerthPreference) AllocationOption
        +calculateFare(BaseFare, int passengerCount) Money
    }

    class GeneralQuotaStrategy {
        +getQuotaCode() QuotaCode
        +validateEligibility(BookingRequest, Instant now) ValidationResult
        +selectCandidateSlot(QuotaPool, BerthPreference) AllocationOption
        +calculateFare(BaseFare, int passengerCount) Money
    }

    class TatkalQuotaStrategy {
        -LocalTime acOpenTime
        -LocalTime nonAcOpenTime
        -double surchargeMultiplier
        +getQuotaCode() QuotaCode
        +validateEligibility(BookingRequest, Instant now) ValidationResult
        +selectCandidateSlot(QuotaPool, BerthPreference) AllocationOption
        +calculateFare(BaseFare, int passengerCount) Money
    }

    class LadiesQuotaStrategy {
        +getQuotaCode() QuotaCode
        +validateEligibility(BookingRequest, Instant now) ValidationResult
        +selectCandidateSlot(QuotaPool, BerthPreference) AllocationOption
        +calculateFare(BaseFare, int passengerCount) Money
    }

    class SeniorCitizenQuotaStrategy {
        -int maleMinAge
        -int femaleMinAge
        +getQuotaCode() QuotaCode
        +validateEligibility(BookingRequest, Instant now) ValidationResult
        +selectCandidateSlot(QuotaPool, BerthPreference) AllocationOption
        +calculateFare(BaseFare, int passengerCount) Money
    }

    class QuotaStrategyRegistry {
        -Map strategies
        +resolve(QuotaCode) QuotaStrategy
    }

    QuotaStrategy <|.. GeneralQuotaStrategy
    QuotaStrategy <|.. TatkalQuotaStrategy
    QuotaStrategy <|.. LadiesQuotaStrategy
    QuotaStrategy <|.. SeniorCitizenQuotaStrategy
    QuotaStrategyRegistry o-- QuotaStrategy : selects at runtime
```

```mermaid
flowchart LR
    Req["BookingRequest<br/>(Quota = TATKAL)"] --> Reg["QuotaStrategyRegistry"]
    Reg -->|"Returns"| Strat["TatkalQuotaStrategy"]
    Strat --> Step1["1. Check 10 AM / 11 AM<br/>Booking Window"]
    Step1 --> Step2["2. Check Tatkal Pool<br/>Availability"]
    Step2 --> Step3["3. Apply Tatkal<br/>Dynamic Surcharge"]
```

---

## 4. Design Pattern 2 — State Pattern (Ticket & PNR Lifecycle)

### Problem Solved
A railway ticket behaves completely differently depending on its current state (`INITIATED`, `PAYMENT_PENDING`, `WAITLISTED`, `RAC`, `CONFIRMED`, `CANCELLED`, `CHARTED`):
- Can a `WAITLISTED` ticket be upgraded? **Yes**, to `RAC`.
- Can an `RAC` ticket be upgraded? **Yes**, to `CONFIRMED`.
- Can a `CONFIRMED` ticket be upgraded? **No**, it is already at the highest travel state—and cancelling it must release a physical berth and trigger an `RAC #1 -> CNF` promotion!
- Can a `CANCELLED` ticket be cancelled again or promoted? **No**, invalid transition!

### Solution with State Pattern
> **In simple words:** Instead of checking `if status == WL ... else if status == RAC ...` everywhere, each Ticket State is an object that knows exactly what actions are allowed in that state and what state comes next.

```mermaid
classDiagram
    class Ticket {
        -String pnrNumber
        -TicketState currentState
        -String allocatedBerthId
        -int queuePosition
        +confirm(String berthId) void
        +assignRac(String sideLowerId, int racPos) void
        +assignWaitlist(int wlPos) void
        +upgrade() void
        +cancel() CancellationResult
        +finalizeChart() void
    }

    class TicketState {
        <<interface>>
        +getStatusName() TicketStatus
        +onUpgrade(Ticket, UpgradeContext) void
        +onCancel(Ticket, CancellationContext) void
        +onChartPrepared(Ticket) void
    }

    class PaymentPendingState {
        +onUpgrade(Ticket, UpgradeContext) void
        +onCancel(Ticket, CancellationContext) void
        +onChartPrepared(Ticket) void
    }

    class WaitlistedState {
        +onUpgrade(Ticket, UpgradeContext) void
        +onCancel(Ticket, CancellationContext) void
        +onChartPrepared(Ticket) void
    }

    class RacState {
        +onUpgrade(Ticket, UpgradeContext) void
        +onCancel(Ticket, CancellationContext) void
        +onChartPrepared(Ticket) void
    }

    class ConfirmedState {
        +onUpgrade(Ticket, UpgradeContext) void
        +onCancel(Ticket, CancellationContext) void
        +onChartPrepared(Ticket) void
    }

    class CancelledState {
        +onUpgrade(Ticket, UpgradeContext) void
        +onCancel(Ticket, CancellationContext) void
        +onChartPrepared(Ticket) void
    }

    Ticket *-- TicketState : delegates behavior
    TicketState <|.. PaymentPendingState
    TicketState <|.. WaitlistedState
    TicketState <|.. RacState
    TicketState <|.. ConfirmedState
    TicketState <|.. CancelledState
```

---

## 5. Design Pattern 3 — Builder Pattern (Validated Multi-Passenger Bookings)

### Problem Solved
Creating a railway booking involves many required and optional parts: train ID, journey date, boarding & destination stations, coach class, quota code, 1–6 passengers (with age, gender, berth preference), contact info, and idempotency key. Passing 12 arguments to a constructor is error-prone and unreadable.

### Solution with Builder Pattern
> **In simple words:** `BookingRequestBuilder` collects each piece of the trip step-by-step and validates all rules (like max 6 passengers per PNR, valid station order, Tatkal passenger limit) before creating an immutable, guaranteed-valid `BookingRequest` object.

```mermaid
flowchart LR
    Step1["1. withTrainAndRoute()<br/>(Train 12951, NDLS -> MMCT)"]
    Step2["2. withJourneyDetails()<br/>(Date, Class 3A, Quota TQ)"]
    Step3["3. addPassenger()<br/>(Name, Age, Gender, Berth Pref)"]
    Step4["4. withIdempotencyKey()<br/>(UUID for U5 Payment Safety)"]
    Step5["5. build() Validation Gate<br/>(1..6 passengers, valid route & age)"]
    Result["Immutable Validated<br/>BookingRequest Object"]

    Step1 --> Step2 --> Step3 --> Step4 --> Step5 --> Result
```

```mermaid
classDiagram
    class BookingRequest {
        -String trainNumber
        -LocalDate journeyDate
        -String sourceStation
        -String destinationStation
        -CoachClass coachClass
        -QuotaCode quotaCode
        -List passengers
        -String idempotencyKey
    }

    class BookingRequestBuilder {
        -String trainNumber
        -LocalDate journeyDate
        -String sourceStation
        -String destinationStation
        -CoachClass coachClass
        -QuotaCode quotaCode
        -List passengers
        -String idempotencyKey
        +withTrain(String, LocalDate) BookingRequestBuilder
        +withRoute(String, String) BookingRequestBuilder
        +withClassAndQuota(CoachClass, QuotaCode) BookingRequestBuilder
        +addPassenger(Passenger) BookingRequestBuilder
        +withIdempotencyKey(String) BookingRequestBuilder
        +build() BookingRequest
    }

    BookingRequestBuilder ..> BookingRequest : validates and constructs
```

---

## 6. How All Patterns Work Together on a Single Booking

```mermaid
flowchart TD
    B["1. Builder Pattern<br/>Constructs valid immutable BookingRequest"]
    S["2. Strategy Pattern<br/>Applies selected Quota rules & calculates fare"]
    I["3. Encapsulated Inventory<br/>Locks partition & acquires Seat Hold safely"]
    P["4. Abstracted Payment<br/>Charges safely using Idempotency-Key"]
    St["5. State Pattern<br/>Places Ticket into CNF / RAC / WL state"]

    B --> S --> I --> P --> St
```

---

> **Next:** See [04 — Ticket Lifecycle & Contiguous Waitlist](04-ticket-lifecycle-and-waitlist.md) for the complete `WL → RAC → CNF` state machine and gap-free cancellation upgrade flowcharts.
