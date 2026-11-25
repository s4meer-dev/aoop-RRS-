# 04 — Ticket State Lifecycle & Contiguous Waitlist Upgrades

> **In simple words:** Every ticket moves through a strict life cycle—starting from **Waitlisted (WL)**, upgrading to **RAC** (shared side-lower berth), and reaching **Confirmed (CNF)** (a full berth). Whenever anyone cancels, our queue shifts forward cleanly by one spot so numbers never have missing gaps like `WL1, WL3`.

---

## 1. The Three Core Travel States (Slide 6)

| State | Meaning in Plain English | Can Board Train? | Berth Assignment |
| :--- | :--- | :--- | :--- |
| **Waitlisted (`WL`)** | Booked into an ordered waiting line (`WL1, WL2...`), no seat yet | **No** (Auto-cancelled & refunded if still WL at charting) | None |
| **RAC (`RAC`)** | *Reservation Against Cancellation* — guaranteed boarding on a shared seat | **Yes** | Shares 1 Side-Lower berth with 1 other RAC passenger |
| **Confirmed (`CNF`)** | Final successful booking with a dedicated full berth | **Yes** | Dedicated full berth (e.g., `Coach B2, Berth 42 - Lower`) |

---

## 2. Complete Ticket / PNR State Machine

```mermaid
stateDiagram-v2
    [*] --> INITIATED : "Builder validates BookingRequest"
    INITIATED --> PAYMENT_PENDING : "Temporary hold acquired"

    PAYMENT_PENDING --> CONFIRMED : "Payment OK + Berth assigned"
    PAYMENT_PENDING --> RAC : "Payment OK + Shared side-lower slot"
    PAYMENT_PENDING --> WAITLISTED : "Payment OK + Contiguous WL# issued"
    PAYMENT_PENDING --> EXPIRED : "Payment failed or Hold TTL expired"

    WAITLISTED --> RAC : "Upstream cancel promotes WL #1"
    RAC --> CONFIRMED : "CNF cancel promotes RAC #1"

    CONFIRMED --> CANCELLED : "User cancels (frees berth -> cascade)"
    RAC --> CANCELLED : "User cancels (frees RAC slot -> WL promotes)"
    WAITLISTED --> CANCELLED : "User cancels or Chart drops unconfirmed WL"

    CONFIRMED --> CHARTED : "Final chart prepared before departure"
    RAC --> CHARTED : "Final chart prepared (travels on shared berth)"
```

---

## 3. New Booking Seat Allocation Flowchart

When a passenger books a seat under a chosen `QuotaStrategy`, the system assigns the highest available tier in strict priority order: **`CNF` → `RAC` → `WL` → `Regret`**.

```mermaid
flowchart TD
    Req(["Valid Booking Request Enters Partition Lock"]) --> CheckCNF{"Full Berth Available<br/>in Quota Pool?"}
    CheckCNF -- "Yes" --> HoldCNF["Acquire Temporary Berth Hold<br/>(Target State: CONFIRMED)"]
    CheckCNF -- "No" --> CheckRAC{"RAC Side-Lower Slot<br/>Available in Pool?"}

    CheckRAC -- "Yes" --> HoldRAC["Acquire Temporary RAC Hold<br/>Assign Next Contiguous RAC#<br/>(Target State: RAC)"]
    CheckRAC -- "No" --> CheckWL{"Current WL Count<br/>< Max WL Limit?"}

    CheckWL -- "Yes" --> HoldWL["Reserve Next Contiguous WL#<br/>(Target State: WAITLISTED)"]
    CheckWL -- "No" --> Regret(["Reject: REGRET / NOT AVAILABLE"])

    HoldCNF --> PayStep["Proceed to U5 Payment Outside Lock"]
    HoldRAC --> PayStep
    HoldWL --> PayStep
```

---

## 4. Contiguous Cancellation & Cascading Upgrade Engine

### Why Legacy Waitlists Break
In traditional systems, cancelling `WL #2` deletes row #2 and leaves `WL #1, WL #3, WL #4`—or cancelling a `CNF` seat leaves a berth idle while `RAC` passengers wait.

### Our Atomic Gap-Free Upgrade Rule (Slide 6)
Every cancellation runs inside the train partition lock as **one atomic operation** covering all three scenarios:

```mermaid
flowchart TD
    Cancel(["Passenger Requests Cancellation"]) --> WhichState{"Current Ticket State?"}

    WhichState -- "CONFIRMED (CNF)" --> FreeBerth["Release Freed Full Berth"]
    FreeBerth --> HasRAC{"Is RAC Queue<br/>Non-Empty?"}
    HasRAC -- "No" --> ReturnPool["Return Berth to Available CNF Pool"]
    HasRAC -- "Yes" --> PromoteRAC["Promote RAC #1 -> CONFIRMED<br/>(Assign Freed Full Berth)"]
    PromoteRAC --> ShiftRAC1["Shift Remaining RAC Queue Up by 1<br/>(RAC #2 -> #1, RAC #3 -> #2 ...)"]
    ShiftRAC1 --> CheckWL1{"Is WL Queue<br/>Non-Empty?"}
    CheckWL1 -- "Yes" --> PromoteWL1["Promote WL #1 -> RAC<br/>(Takes Vacated RAC Slot)"]
    PromoteWL1 --> ShiftWL1["Shift Remaining WL Queue Up by 1<br/>(WL #2 -> #1, WL #3 -> #2 ...)"]
    CheckWL1 -- "No" --> Done["Update PNR States & Trigger Refund"]
    ShiftWL1 --> Done
    ReturnPool --> Done

    WhichState -- "RAC" --> RemoveRAC["Remove Cancelled RAC Entry at Pos k"]
    RemoveRAC --> ShiftRAC2["Shift RAC #(k+1)..R Up by 1"]
    ShiftRAC2 --> CheckWL2{"Is WL Queue<br/>Non-Empty?"}
    CheckWL2 -- "Yes" --> PromoteWL2["Promote WL #1 -> Last RAC Slot"]
    PromoteWL2 --> ShiftWL2["Shift Remaining WL Queue Up by 1"]
    CheckWL2 -- "No" --> Done
    ShiftWL2 --> Done

    WhichState -- "WAITLISTED (WL)" --> RemoveWL["Remove Cancelled WL Entry at Pos k"]
    RemoveWL --> ShiftWL3["Shift WL #(k+1)..W Up by 1"]
    ShiftWL3 --> Done
```

---

## 5. Step-by-Step Visual Example of a Gap-Free Cascade

Suppose **Passenger A** (Confirmed on `Berth B2-42`) cancels while 2 passengers are in RAC and 3 passengers are Waitlisted:

```mermaid
flowchart LR
    subgraph Before["Before Cancellation"]
        direction TB
        B_CNF["CNF: Passenger A (B2-42)"]
        B_RAC1["RAC #1: Passenger B (SL-7)"]
        B_RAC2["RAC #2: Passenger C (SL-7)"]
        B_WL1["WL #1: Passenger D"]
        B_WL2["WL #2: Passenger E"]
        B_WL3["WL #3: Passenger F"]
    end

    subgraph After["After Atomic Cascade (Zero Gaps!)"]
        direction TB
        A_CAN["CANCELLED: Passenger A (Refunded)"]
        A_CNF["CNF: Passenger B (Promoted to B2-42!)"]
        A_RAC1["RAC #1: Passenger C (Shifted RAC#2 -> #1)"]
        A_RAC2["RAC #2: Passenger D (Promoted WL#1 -> RAC#2!)"]
        A_WL1["WL #1: Passenger E (Shifted WL#2 -> #1)"]
        A_WL2["WL #2: Passenger F (Shifted WL#3 -> #2)"]
    end

    B_CNF -.->|"Cancels"| A_CAN
    B_RAC1 -->|"Promotes to CNF"| A_CNF
    B_RAC2 -->|"Shifts up -1"| A_RAC1
    B_WL1 -->|"Promotes to RAC"| A_RAC2
    B_WL2 -->|"Shifts up -1"| A_WL1
    B_WL3 -->|"Shifts up -1"| A_WL2
```

Notice the invariant:
- Before: `RAC = {1, 2}` and `WL = {1, 2, 3}` (contiguous).
- After: `RAC = {1, 2}` and `WL = {1, 2}` (still 100% contiguous, zero gaps, and the freed berth `B2-42` is immediately utilized).

---

## 6. Pre-Departure Chart Preparation Flow (Slide 3 + Slide 6)

Before departure (typically 4 hours prior in IRCTC), the **Chart** (final passenger manifest) is prepared:

```mermaid
flowchart TD
    Start(["Chart Preparation Triggered<br/>(T - 4 Hours Before Departure)"]) --> Lock["Lock Train Journey Instance<br/>(Stop New Online Bookings)"]
    Lock --> FinalUpgrade["Run Final Upgrade Pass Across<br/>Any Unassigned Berths"]
    FinalUpgrade --> MarkCNF["Lock All CONFIRMED Tickets -> CHARTED"]
    MarkCNF --> MarkRAC["Lock All Remaining RAC Tickets -> CHARTED<br/>(Confirm Shared Side-Lower Pairs)"]
    MarkRAC --> DropWL{"Any Remaining<br/>WAITLISTED Tickets?"}
    DropWL -- "Yes" --> AutoCancel["Auto-Cancel Remaining WL Tickets"]
    AutoCancel --> AutoRefund["U5: Trigger Automated Full Refund"]
    AutoRefund --> Publish(["Publish Digital Passenger Chart"])
    DropWL -- "No" --> Publish
```

---

> **Next:** See [05 — Fault-Tolerant Payments (Unit 5)](05-fault-tolerant-payments.md) to learn how payments, timeouts, and automatic refunds never leave a passenger without a ticket or money.
