# 05 — Fault-Tolerant Payments (Unit 5)

> **In simple words:** One of the biggest nightmares in ticket booking is *"Money got deducted from my bank account, but no ticket was issued!"* Unit 5 solves this permanently using three safeguards: **Idempotency Keys** (so retries never charge twice), **Timeout Reconciliation** (checking late bank replies automatically), and **Automated Refund Compensation** (giving your money back automatically if the seat expired while the bank was slow).

---

## 1. The Three Payment Safeguards (Slide 5 — Unit 5)

| Mechanism | Plain-English Meaning | What Failure It Eliminates |
| :--- | :--- | :--- |
| **Idempotency Keys** | A unique ID attached to every payment attempt (`UUID`). If the browser refreshes or the network retries, the server returns the *first* result instead of charging again. | **Double charging** during shaky mobile internet or panic double-clicks at 10 AM |
| **Timeout Reconciliation** | If the bank replies too slowly (`Gateway Timeout`), we do **not** blindly mark the booking failed. We mark it `TIMEOUT_PENDING` and safely poll/reconcile with the bank. | **"Limbo bookings"** where the bank debits money after the booking app gave up |
| **Automated Refund Compensation** | If the bank confirms payment *after* our temporary Seat Hold expired (or after another check failed), a **Saga Compensation** step triggers an automatic refund immediately. | **Money deducted, no ticket** without manual customer-support intervention |

---

## 2. Payment Transaction State Lifecycle

```mermaid
stateDiagram-v2
    [*] --> CREATED : "PaymentIntent created with unique Idempotency-Key"
    CREATED --> PROCESSING : "Sent to Bank / Payment Gateway"

    PROCESSING --> SUCCEEDED : "Gateway confirms debit within window"
    PROCESSING --> FAILED : "Gateway declines / insufficient funds"
    PROCESSING --> TIMEOUT_PENDING : "Gateway timeout (bank reply unknown)"

    TIMEOUT_PENDING --> RECONCILED_SUCCESS : "Reconciler confirms bank debited"
    TIMEOUT_PENDING --> RECONCILED_FAILED : "Reconciler confirms bank never debited"

    SUCCEEDED --> REFUND_INITIATED : "User cancels OR late hold conflict"
    RECONCILED_SUCCESS --> REFUND_INITIATED : "Hold expired before bank replied"
    REFUND_INITIATED --> REFUNDED : "Automated compensation completed"
```

---

## 3. Idempotency Key Flow (Zero Double-Charging)

```mermaid
sequenceDiagram
    actor User as Passenger
    participant Book as Booking Context
    participant Pay as Payment Context (U5)
    participant Ledger as Idempotency Ledger
    participant Bank as External Payment Gateway

    User->>Book: Pay Request #1 (Idempotency-Key: "req-991a")
    Book->>Pay: processPayment(key="req-991a", amount=1850)
    Pay->>Ledger: Check Key "req-991a"
    Ledger-->>Pay: Not Found -> Store Lock Record (PROCESSING)
    Pay->>Bank: Charge ₹1,850 (ref="req-991a")
    Bank-->>Pay: Success (txnId="GW-7781")
    Pay->>Ledger: Save Result (SUCCEEDED, txnId="GW-7781")
    Note over User,Pay: Network drops before response reaches user!

    User->>Book: Retry Pay Request #2 (Same Key: "req-991a")
    Book->>Pay: processPayment(key="req-991a", amount=1850)
    Pay->>Ledger: Check Key "req-991a"
    Ledger-->>Pay: Found Existing SUCCEEDED (txnId="GW-7781")
    Pay-->>Book: Return Cached PaymentSucceeded (No Second Bank Call!)
    Book-->>User: PNR Confirmed (Charged Only Once!)
```

---

## 4. Gateway Timeout Reconciliation & Late-Success Compensation

When a bank gateway hangs during the 10 AM Tatkal rush, our system cleanly separates **what the bank did** from **whether the seat hold is still valid**:

```mermaid
sequenceDiagram
    participant Book as Booking Context
    participant Inv as Inventory Context
    participant Pay as Payment Context (U5)
    participant Bank as External Payment Gateway
    participant Job as Timeout Reconciler Worker

    Book->>Inv: Hold Seat B2-42 (TTL = 180s)
    Book->>Pay: Charge ₹1,850 (Idempotency-Key="req-552b")
    Pay->>Bank: Charge Request
    Note over Pay,Bank: Gateway does not respond within timeout window!
    Pay-->>Book: Status = TIMEOUT_PENDING
    Book-->>Book: Mark PNR = PAYMENT_PENDING (Hold awaits reconcile)

    loop Background Reconciliation Check
        Job->>Pay: Pick TIMEOUT_PENDING transactions
        Job->>Bank: Query Status by Reference ("req-552b")
    end

    alt Bank Replies: NEVER DEBITED
        Bank-->>Job: Status = FAILED
        Job->>Pay: Mark RECONCILED_FAILED
        Job->>Inv: Release Seat Hold B2-42
        Job->>Book: Mark PNR = EXPIRED (Zero money lost)
    else Bank Replies: DEBITED + Seat Hold STILL VALID
        Bank-->>Job: Status = SUCCEEDED
        Job->>Pay: Mark RECONCILED_SUCCESS
        Job->>Inv: Commit Berth B2-42 -> CONFIRMED
        Job->>Book: Upgrade PNR -> CONFIRMED (Passenger gets ticket!)
    else Bank Replies: DEBITED, But Hold TTL Already Expired
        Bank-->>Job: Status = SUCCEEDED
        Job->>Pay: Mark RECONCILED_SUCCESS
        Job->>Inv: Check if Berth Still Available
        Inv-->>Job: Seat Taken by Another User!
        Job->>Pay: Trigger Automated Refund Compensation (Saga)
        Pay->>Bank: Initiate Instant Refund ₹1,850
        Job->>Book: Mark PNR = CANCELLED_REFUNDED
    end
```

---

## 5. Automated Refund Compensation Decision Flowchart (Saga Pattern)

A refund is triggered automatically—with zero manual ticket-desk intervention—under four clear scenarios:

```mermaid
flowchart TD
    Trigger{"Compensation Trigger Event"}
    Trigger --> C1["1. Late Bank Success<br/>After Seat Hold Expired"]
    Trigger --> C2["2. Passenger Cancels<br/>CNF / RAC / WL Ticket"]
    Trigger --> C3["3. Chart Preparation Drops<br/>Unconfirmed WL Ticket"]
    Trigger --> C4["4. Downstream Allocation<br/>Failure After Debit"]

    C1 --> Calc["Calculate Refund Amount<br/>(100% Full Refund)"]
    C2 --> Policy["Apply Quota Refund Strategy<br/>(e.g., General vs. Tatkal Rules)"]
    C3 --> Calc
    C4 --> Calc

    Calc --> CreateRef["Create Unique Refund Record<br/>(Idempotent Refund Key)"]
    Policy --> CreateRef

    CreateRef --> SendBank["Submit Refund to Payment Gateway"]
    SendBank --> Retry{"Gateway Accepted?"}
    Retry -- "Yes" --> Settled(["Mark REFUNDED & Notify Passenger"])
    Retry -- "Transient Error" --> Backoff["Retry with Exponential Backoff<br/>Until Settled"]
    Backoff --> SendBank
```

---

> **Next:** See [07 — Project Structure & Implementation Roadmap](07-project-structure-and-roadmap.md) to inspect how all modules, folders, tests, and implementation phases fit together.
