# 02 — System Architecture, Bounded Contexts (U7) & Tatkal Backpressure (U6)

> **In simple words:** Instead of one crowded counter where everyone pushes into one giant ledger at 10:00 AM, think of our system as a well-organized railway terminal: a **controlled entry gate (U6)** lets manageable batches inside so the hall never crashes, and **four specialized departments (U7)**—Booking, Inventory, Waitlist, and Payment—each manage their own work independently.

---

## 1. Unit 7 — Isolated Bounded Contexts

### Why Bounded Contexts?
In a traditional **monolithic locking system**, inventory counts, payment logs, waitlist numbers, and passenger records all share one database lock. If the payment gateway slows down, seat queries freeze; if waitlist shifts fail, the whole booking app stalls.

With **Bounded Contexts (Unit 7)**, we split the domain into **four isolated services**. Each context owns its own data model, internal invariants, and locks—and communicates with other contexts strictly through well-defined service interfaces or domain events.

```mermaid
flowchart TB
    Client["Client Layer<br/>(Web / Mobile / CLI Simulator)"]
    Gate["U6: Backpressure Admission Gate<br/>(Rate Limiter + Bounded Queue + Worker Pool)"]

    subgraph U7["U7: Four Isolated Bounded Contexts"]
        direction TB
        subgraph BC1["Booking Context"]
            B_Logic["PNR Orchestrator<br/>Ticket Builder & State Machine<br/>Cancellation Coordinator"]
            B_Data[("Booking Store<br/>PNRs, Passenger Lists,<br/>Ticket States")]
            B_Logic --- B_Data
        end

        subgraph BC2["Inventory Context"]
            I_Logic["Train & Rake Manager<br/>Quota Pools (Strategy)<br/>Partitioned Seat Locks & Charting"]
            I_Data[("Inventory Store<br/>Coaches, Berths,<br/>Holds & Quota Counts")]
            I_Logic --- I_Data
        end

        subgraph BC3["Waitlist Context"]
            W_Logic["Contiguous RAC & WL Manager<br/>FIFO Cascade Promoter<br/>Gap-Free Re-indexer"]
            W_Data[("Waitlist Store<br/>Ordered RAC# & WL#<br/>Priority Queues")]
            W_Logic --- W_Data
        end

        subgraph BC4["Payment Context (U5)"]
            P_Logic["Idempotent Payment Processor<br/>Timeout Reconciler<br/>Refund Compensation Engine"]
            P_Data[("Payment Ledger<br/>Idempotency Keys,<br/>Txn States & Refunds")]
            P_Logic --- P_Data
        end
    end

    Client --> Gate
    Gate --> B_Logic
    B_Logic <-->|"Hold / Confirm / Release Seat"| I_Logic
    B_Logic <-->|"Enqueue / Upgrade / Cancel Slot"| W_Logic
    B_Logic <-->|"Authorize / Reconcile / Refund"| P_Logic
    I_Logic -.->|"Berth Freed Event on Cancel"| W_Logic
```

### Bounded Context Responsibilities & Isolation Rules

| Bounded Context | Owns Exclusively | Must NEVER Touch Directly | Exposed Contract |
| :--- | :--- | :--- | :--- |
| **Booking Context** | PNR records, Passenger details, Booking Lifecycle State, Request validation | Raw berth locks, payment card/bank state, internal waitlist pointers | `createBooking()`, `cancelBooking()`, `getPnrStatus()` |
| **Inventory Context** | Train schedules, Coaches, Berths, Quota capacities (`GN`, `TQ`, `LD`, `SS`), Temporary seat holds, Final Chart | Payment transactions, PNR state objects | `checkAvailability()`, `acquireHold()`, `commitBerth()`, `releaseBerth()`, `prepareChart()` |
| **Waitlist Context** | Ordered `RAC` & `WL` queues per `(Train, Date, Class, Quota)`, contiguous positions | Berth layouts, payment gateway logic | `allocateWaitlistOrRac()`, `promoteOnCancellation()`, `removeAndShiftContiguous()` |
| **Payment Context** | Idempotency ledger, gateway adapters, timeout reconciliation jobs, refund records | Seat inventory, waitlist positions | `processPayment(idempotencyKey)`, `reconcilePending()`, `triggerRefund()` |

---

## 2. Unit 6 — 10 AM Tatkal Rush Backpressure & Concurrency

> **In simple words:** When 1,00,000 passengers click "Book" at `10:00:00 AM`, trying to handle all 1,00,000 requests on 1,00,000 simultaneous threads crashes the server. **Backpressure** puts a fixed-size waiting line (`Bounded Queue`) in front of a controlled team of worker threads (`Throttling`). Requests within capacity get smooth service; extreme overflow gets a quick, polite `"Server Busy, Try Again"` message instead of crashing everyone's session.

### Backpressure Pipeline Architecture

```mermaid
flowchart LR
    Surge["10:00 AM Tatkal Surge<br/>(Thousands of Concurrent Requests)"] --> RateCheck{"Per-User / Token<br/>Rate Limit OK?"}
    RateCheck -- "Bot / Burst Violation" --> Drop429["Reject Immediately<br/>(HTTP 429 Too Many Requests)"]
    RateCheck -- "Valid Request" --> QueueCheck{"Bounded Queue<br/>(Capacity = N)<br/>Has Space?"}

    QueueCheck -- "Queue Full<br/>(Backpressure Triggered)" --> FastFail["Graceful Load Shedding<br/>(HTTP 503 Retry-After)<br/>Zero Thread Exhaustion"]
    QueueCheck -- "Admitted to Queue" --> BQueue["Bounded FIFO Queue<br/>(Fixed Memory Footprint)"]

    BQueue --> Pool["Throttled Worker Thread Pool<br/>(Fixed Core/Max Threads)"]
    Pool --> Partition["Route to Partitioned Lock<br/>(Train + Date + Class + Quota)"]
    Partition --> Engine["Execute Booking Flow<br/>(Hold -> Pay -> Confirm/WL)"]
```

### Why Bounded Queues + Throttling Prevent Crashes

| Factor | Unbounded System (Naive) | Our Backpressure Design (Unit 6) |
| :--- | :--- | :--- |
| **Thread Count** | Spawns thousands of threads until OS/JVM runs out of memory | Fixed worker pool size matched to CPU & lock throughput |
| **Queue Memory** | Unbounded queue grows indefinitely during bank slowdowns (`OutOfMemoryError`) | **Bounded queue** strictly caps memory footprint |
| **Latency Under Load** | Every user waits 60+ seconds and times out simultaneously | Admitted requests finish fast; overflow fails fast in milliseconds |
| **Recovery** | Server crashes and requires manual restart at 10:02 AM | Server stays alive, drains queue smoothly, and recovers automatically |

---

## 3. Concurrency Control: Avoiding Both Race Conditions & Global Freeze

### Monolithic Global Lock vs. Partitioned Locking

Traditional systems lock the **entire database**, which forces a passenger booking **Rajdhani Express (New Delhi → Mumbai)** to wait behind someone booking **Shatabdi Express (Chennai → Bengaluru)**!

Instead, our **Inventory & Waitlist Contexts** use **Partitioned Locking** keyed by:

`Partition Key = (TrainNumber, JourneyDate, CoachClass, QuotaCode)`

```mermaid
flowchart TD
    ReqA["Thread 1: Book Train 12951<br/>Date: 15-Dec | Class: 3A | Quota: TQ"]
    ReqB["Thread 2: Book Train 12951<br/>Date: 15-Dec | Class: 3A | Quota: TQ"]
    ReqC["Thread 3: Book Train 12301<br/>Date: 15-Dec | Class: 3A | Quota: TQ"]

    ReqA --> Lock1["Lock Partition:<br/>(12951, 15-Dec, 3A, TQ)"]
    ReqB --> Lock1
    ReqC --> Lock2["Lock Partition:<br/>(12301, 15-Dec, 3A, TQ)"]

    Lock1 -->|"Serialized Safely:<br/>Thread 1 gets Berth 42;<br/>Thread 2 gets Berth 43 (Zero Double Booking!)"| Result1["Independent Execution"]
    Lock2 -->|"Runs in True Parallel!<br/>Never Blocked by Train 12951"| Result2["Independent Execution"]
```

### Two-Phase Reservation Window (Why We Hold Before Charging)
We **never** hold an exclusive in-memory lock while waiting 5–30 seconds for a bank payment gateway! Look at how we keep lock hold times in **microseconds**:
1. **Acquire Partition Lock (Microseconds):** Check quota availability, reserve a temporary **Seat Hold** (e.g., valid for `TTL = 180 seconds`) or reserve a tentative queue slot, then **immediately release the lock**.
2. **Process Payment Outside the Lock (Seconds):** Call the Payment Context using a unique `Idempotency-Key`. Other users booking the same train can still reserve other seats concurrently!
3. **Finalize or Release (Microseconds):**
   - If Payment **Succeeds** within TTL → convert the temporary hold into a **Confirmed / RAC / WL** allocation.
   - If Payment **Fails** or **TTL Expires** → release the held berth back to the pool cleanly.
   - If Payment **Arrives Late After Expiry** → trigger **Unit 5 Automated Refund Compensation**.

---

## 4. End-to-End Booking Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    actor User as Passenger
    participant Gate as U6: Backpressure Gate
    participant Book as U7: Booking Context
    participant Inv as U7: Inventory Context
    participant WL as U7: Waitlist Context
    participant Pay as U7/U5: Payment Context

    User->>Gate: Submit BookingRequest (Train, Date, Class, Quota, Passengers)
    alt Bounded Queue Full
        Gate-->>User: 503 Service Busy (Retry-After)
    else Admitted by Throttled Worker
        Gate->>Book: Dispatch Request
        Book->>Book: Build & Validate Request (Builder Pattern)
        Book->>Inv: Check & Hold Seat via QuotaStrategy (Partition Lock)

        alt Full Berth Available
            Inv-->>Book: SeatHoldCreated (Berth Held, TTL=180s)
        else RAC Slot Available
            Inv-->>Book: RacHoldCreated (Shared Side-Lower Slot Held)
        else Waitlist Available
            Inv-->>Book: Exhausted (Route to Waitlist)
            Book->>WL: Reserve Tentative Contiguous WL Slot
            WL-->>Book: Tentative WL# Reserved
        else Waitlist Full
            Inv-->>Book: Regret / Sold Out
            Book-->>User: Booking Rejected (No Seats/WL Available)
        end

        Book->>Pay: Charge(amount, idempotencyKey)
        alt Payment Succeeded (Within Hold TTL)
            Pay-->>Book: PaymentSucceeded
            Book->>Inv: Commit Hold -> Permanent Berth (or confirm WL/RAC)
            Book->>Book: Transition TicketState -> CNF / RAC / WL
            Book-->>User: PNR Issued Successfully
        else Gateway Timeout
            Pay-->>Book: PendingReconciliation
            Book-->>User: PNR Marked PENDING (Safe Auto-Reconcile Active)
        else Payment Failed or Hold Expired
            Pay-->>Book: PaymentFailed / LateSuccessAfterExpiry
            opt Late Success Charged User
                Book->>Pay: Trigger Automated Refund Compensation
            end
            Book->>Inv: Release Temporary Seat Hold
            Book-->>User: Booking Failed (No Charge / Refund Initiated)
        end
    end
```

---

> **Next:** See [03 — OOP Design & Design Patterns](03-oop-and-design-patterns.md) for how **Encapsulation, Inheritance, Polymorphism, Abstraction** and **Strategy, State, Builder** structure the codebase cleanly.
