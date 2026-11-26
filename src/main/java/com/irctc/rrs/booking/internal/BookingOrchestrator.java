package com.irctc.rrs.booking.internal;

import com.irctc.rrs.booking.BookingRequest;
import com.irctc.rrs.booking.BookingRequest.PassengerInput;
import com.irctc.rrs.booking.BookingRequestBuilder;
import com.irctc.rrs.booking.BookingService;
import com.irctc.rrs.booking.internal.state.Ticket;
import com.irctc.rrs.common.concurrency.PartitionLockManager;
import com.irctc.rrs.common.events.PaymentReconciledEvent;
import com.irctc.rrs.common.exception.ResourceNotFoundException;
import com.irctc.rrs.common.exception.SeatHoldExpiredException;
import com.irctc.rrs.common.exception.SystemBusyException;
import com.irctc.rrs.common.id.IdGenerator;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.inventory.InventoryService;
import com.irctc.rrs.inventory.InventoryService.BerthReleaseResult;
import com.irctc.rrs.inventory.InventoryService.BerthSnapshot;
import com.irctc.rrs.inventory.InventoryService.ChartSummary;
import com.irctc.rrs.inventory.InventoryService.HeldTicketAssignment;
import com.irctc.rrs.inventory.InventoryService.HoldAllocationType;
import com.irctc.rrs.inventory.InventoryService.HoldResult;
import com.irctc.rrs.inventory.InventoryService.PassengerEligibilityInfo;
import com.irctc.rrs.inventory.InventoryService.SeatHoldReceipt;
import com.irctc.rrs.payment.PaymentService;
import com.irctc.rrs.payment.PaymentService.PaymentCommand;
import com.irctc.rrs.payment.PaymentService.PaymentMode;
import com.irctc.rrs.payment.PaymentService.PaymentResult;
import com.irctc.rrs.payment.PaymentService.PaymentStatus;
import com.irctc.rrs.payment.PaymentService.RefundCommand;
import com.irctc.rrs.payment.PaymentService.RefundReceipt;
import com.irctc.rrs.waitlist.WaitlistService;
import com.irctc.rrs.waitlist.WaitlistService.CascadeUpgradeResult;
import com.irctc.rrs.waitlist.WaitlistService.ShiftedEntry;
import com.irctc.rrs.waitlist.WaitlistService.WaitlistEntrySnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BookingOrchestrator implements BookingService {

    private final TatkalAdmissionGate admissionGate;
    private final InventoryService inventoryService;
    private final WaitlistService waitlistService;
    private final PaymentService paymentService;
    private final PartitionLockManager lockManager;
    private final TimeProvider timeProvider;
    private final IdGenerator idGenerator;
    private final BookingTelemetryDisruptor telemetry;

    private final Map<String, BookingPnr> pnrsByNumber = new ConcurrentHashMap<>();
    private final Map<String, BookingPnr> pnrsByIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketsById = new ConcurrentHashMap<>();
    private final Deque<BookingPnr> recentPnrs = new ConcurrentLinkedDeque<>();

    private final Counter confirmedCounter;
    private final Counter racCounter;
    private final Counter wlCounter;

    public BookingOrchestrator(
            TatkalAdmissionGate admissionGate,
            InventoryService inventoryService,
            WaitlistService waitlistService,
            PaymentService paymentService,
            PartitionLockManager lockManager,
            TimeProvider timeProvider,
            IdGenerator idGenerator,
            BookingTelemetryDisruptor telemetry,
            MeterRegistry meterRegistry
    ) {
        this.admissionGate = admissionGate;
        this.inventoryService = inventoryService;
        this.waitlistService = waitlistService;
        this.paymentService = paymentService;
        this.lockManager = lockManager;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
        this.telemetry = telemetry;

        this.confirmedCounter = Counter.builder("rrs.bookings.confirmed").register(meterRegistry);
        this.racCounter = Counter.builder("rrs.bookings.rac").register(meterRegistry);
        this.wlCounter = Counter.builder("rrs.bookings.waitlisted").register(meterRegistry);
    }

    @Override
    public BookingResponse createBooking(BookingRequest request) {
        BookingPnr existing = pnrsByIdempotencyKey.get(request.idempotencyKey());
        if (existing != null) {
            return toResponse(existing, true);
        }

        return admissionGate.admitAndExecute(request.userId(), request.bypassRateLimit(),
                () -> executeBookingCore(request));
    }

    private BookingResponse executeBookingCore(BookingRequest request) {
        BookingPnr existing = pnrsByIdempotencyKey.get(request.idempotencyKey());
        if (existing != null) {
            return toResponse(existing, true);
        }

        PartitionKey key = new PartitionKey(
                request.trainNumber(), request.journeyDate(), request.coachClass(), request.quotaCode()
        );

        List<PassengerEligibilityInfo> eligInfos = request.passengers().stream()
                .map(p -> new PassengerEligibilityInfo(p.name(), p.age(), p.gender()))
                .toList();
        List<BerthPreference> prefs = request.passengers().stream()
                .map(PassengerInput::preference)
                .toList();

        // Phase 1: Acquire short-lived hold inside Partition Lock (microseconds)
        HoldResult holdResult = inventoryService.acquireHoldBatch(
                key, eligInfos, prefs, request.bypassTatkalClock()
        );

        String pnrNumber = idGenerator.nextPnr();
        Instant now = timeProvider.now();

        List<Ticket> tickets = new ArrayList<>(request.passengers().size());
        for (int i = 0; i < request.passengers().size(); i++) {
            PassengerInput p = request.passengers().get(i);
            String ticketId = idGenerator.nextId("TKT");
            Ticket ticket = new Ticket(ticketId, pnrNumber, p.name(), p.age(), p.gender());
            ticket.markHoldAcquired("Hold " + holdResult.holds().get(i).holdId() + " acquired");
            tickets.add(ticket);
            ticketsById.put(ticketId, ticket);
        }

        BookingPnr pnr = new BookingPnr(
                pnrNumber,
                request.userId(),
                key,
                request.sourceStation(),
                request.destinationStation(),
                holdResult.totalFare(),
                request.idempotencyKey(),
                tickets,
                holdResult.holds(),
                now
        );

        BookingPnr raced = pnrsByIdempotencyKey.putIfAbsent(request.idempotencyKey(), pnr);
        if (raced != null) {
            inventoryService.releaseExpiredOrFailedHolds(key, holdResult.holds());
            return toResponse(raced, true);
        }
        pnrsByNumber.put(pnrNumber, pnr);
        recentPnrs.addFirst(pnr);
        while (recentPnrs.size() > 300) {
            recentPnrs.pollLast();
        }

        // Simulate hold TTL expiration while bank gateway hangs (for deterministic U5 Saga demo)
        if (request.paymentMode() == PaymentMode.SLOW_SUCCESS_AFTER_HOLD_EXPIRY) {
            inventoryService.releaseExpiredOrFailedHolds(key, holdResult.holds());
        }

        // Phase 2: Process Payment OUTSIDE Inventory Lock (Unit 5)
        PaymentResult payResult = paymentService.processPayment(new PaymentCommand(
                pnrNumber, request.idempotencyKey(), holdResult.totalFare(), request.paymentMode()
        ));
        pnr.setPaymentId(payResult.paymentId());
        pnr.setPaymentStatus(payResult.status().name());

        // Phase 3: Commit or Release / Reconcile
        if (payResult.status() == PaymentStatus.SUCCEEDED) {
            commitPnrAllocations(pnr, holdResult.holds());
            pnr.setStatusMessage("Booking confirmed and tickets issued.");
            telemetry.publish("BOOKING_CONFIRMED", "PNR " + pnrNumber + " (" + pnr.aggregateStatus() + ")");
        } else if (payResult.status() == PaymentStatus.TIMEOUT_PENDING) {
            pnr.setStatusMessage("Bank gateway timed out; PNR marked PAYMENT_PENDING for automatic reconciliation.");
            telemetry.publish("PAYMENT_TIMEOUT", "PNR " + pnrNumber + " awaiting reconciliation");
        } else {
            inventoryService.releaseExpiredOrFailedHolds(key, holdResult.holds());
            for (Ticket t : pnr.getTickets()) {
                t.expire("Payment declined by bank");
            }
            pnr.clearPendingHolds();
            pnr.setStatusMessage("Payment declined; seat hold released cleanly with zero charge.");
            telemetry.publish("PAYMENT_FAILED", "PNR " + pnrNumber + " hold released");
        }

        return toResponse(pnr, payResult.idempotentReplay());
    }

    @EventListener
    public void onPaymentReconciled(PaymentReconciledEvent event) {
        BookingPnr pnr = pnrsByNumber.get(event.pnrNumber());
        if (pnr == null) {
            return;
        }
        synchronized (pnr) {
            if (pnr.aggregateStatus() != TicketStatus.PAYMENT_PENDING) {
                return;
            }
            if (event.succeeded()) {
                try {
                    commitPnrAllocations(pnr, pnr.getPendingHolds());
                    pnr.setPaymentStatus(PaymentStatus.RECONCILED_SUCCESS.name());
                    pnr.setStatusMessage("Reconciliation succeeded: ticket issued from held seat.");
                    telemetry.publish("RECONCILED_TICKET", "PNR " + pnr.getPnrNumber() + " -> " + pnr.aggregateStatus());
                } catch (SeatHoldExpiredException expired) {
                    for (Ticket t : pnr.getTickets()) {
                        if (t.getStatus() == TicketStatus.PAYMENT_PENDING) {
                            t.cancel("Seat hold expired before late bank confirmation");
                        }
                    }
                    pnr.clearPendingHolds();
                    RefundReceipt refund = paymentService.triggerRefundCompensation(new RefundCommand(
                            pnr.getPaymentId(),
                            pnr.getPnrNumber(),
                            pnr.getTotalFare(),
                            "Automated Saga Refund: Late bank settlement after seat hold expired"
                    ));
                    pnr.setPaymentStatus(refund.status());
                    pnr.setStatusMessage("Seat hold expired during gateway delay; automated full refund issued (" + refund.refundId() + ").");
                    telemetry.publish("SAGA_AUTO_REFUND", "PNR " + pnr.getPnrNumber() + " refunded via " + refund.refundId());
                }
            } else {
                inventoryService.releaseExpiredOrFailedHolds(pnr.getPartitionKey(), pnr.getPendingHolds());
                for (Ticket t : pnr.getTickets()) {
                    if (t.getStatus() == TicketStatus.PAYMENT_PENDING) {
                        t.expire("Reconciled as unpaid by bank");
                    }
                }
                pnr.clearPendingHolds();
                pnr.setPaymentStatus(PaymentStatus.RECONCILED_FAILED.name());
                pnr.setStatusMessage("Reconciliation confirmed bank never debited; seat hold released.");
            }
        }
    }

    private void commitPnrAllocations(BookingPnr pnr, List<SeatHoldReceipt> holds) {
        if (holds == null || holds.isEmpty()) {
            throw new SeatHoldExpiredException("No active holds for PNR " + pnr.getPnrNumber());
        }
        List<HeldTicketAssignment> assignments = new ArrayList<>(holds.size());
        for (int i = 0; i < holds.size(); i++) {
            assignments.add(new HeldTicketAssignment(holds.get(i), pnr.getTickets().get(i).getTicketId()));
        }

        lockManager.runWithLock(pnr.getPartitionKey(), () -> {
            inventoryService.commitHeldAllocations(pnr.getPartitionKey(), assignments);
            for (int i = 0; i < holds.size(); i++) {
                SeatHoldReceipt hold = holds.get(i);
                Ticket ticket = pnr.getTickets().get(i);
                if (hold.allocationType() == HoldAllocationType.CNF_HOLD) {
                    ticket.confirm(hold.coachCode(), hold.berthNumber(), hold.berthType(),
                            "Confirmed berth " + hold.coachCode() + "-" + hold.berthNumber());
                    confirmedCounter.increment();
                } else if (hold.allocationType() == HoldAllocationType.RAC_HOLD) {
                    WaitlistEntrySnapshot racEntry = waitlistService.enqueueRac(
                            pnr.getPartitionKey(), ticket.getTicketId(), pnr.getPnrNumber(),
                            ticket.getPassengerName(), hold.coachCode(), hold.berthNumber()
                    );
                    ticket.assignRac(hold.coachCode(), hold.berthNumber(), racEntry.contiguousPosition(),
                            "Assigned RAC #" + racEntry.contiguousPosition() + " on " + hold.coachCode() + "-" + hold.berthNumber());
                    racCounter.increment();
                } else {
                    WaitlistEntrySnapshot wlEntry = waitlistService.enqueueWaitlist(
                            pnr.getPartitionKey(), ticket.getTicketId(), pnr.getPnrNumber(), ticket.getPassengerName()
                    );
                    ticket.assignWaitlist(wlEntry.contiguousPosition(),
                            "Assigned Waitlist WL #" + wlEntry.contiguousPosition());
                    wlCounter.increment();
                }
            }
        });
        pnr.clearPendingHolds();
    }

    @Override
    public BookingResponse getBooking(String pnrNumber) {
        BookingPnr pnr = pnrsByNumber.get(pnrNumber);
        if (pnr == null) {
            throw new ResourceNotFoundException("PNR not found: " + pnrNumber);
        }
        return toResponse(pnr, false);
    }

    @Override
    public List<BookingResponse> listBookings(int limit) {
        return recentPnrs.stream()
                .limit(Math.max(1, limit))
                .map(p -> toResponse(p, false))
                .toList();
    }

    @Override
    public CancellationResponse cancelBooking(String pnrNumber, String reason) {
        BookingPnr pnr = pnrsByNumber.get(pnrNumber);
        if (pnr == null) {
            throw new ResourceNotFoundException("PNR not found: " + pnrNumber);
        }
        String effectiveReason = (reason == null || reason.isBlank()) ? "Cancelled by passenger" : reason;
        PartitionKey key = pnr.getPartitionKey();

        List<String> upgradeDescriptions = new ArrayList<>();
        Money[] totalRefund = {Money.ZERO};

        lockManager.runWithLock(key, () -> {
            int ticketCount = pnr.getTickets().size();
            Money perTicketPaid = Money.of(pnr.getTotalFare().amount().doubleValue() / Math.max(1, ticketCount));

            for (Ticket ticket : pnr.getTickets()) {
                TicketStatus previousStatus = ticket.getStatus();
                String coachCode = ticket.getAllocatedCoach();
                Integer berthNumber = ticket.getAllocatedBerth();

                ticket.cancel(effectiveReason);
                Money refundForTicket = inventoryService.calculateRefund(key.quotaCode(), perTicketPaid, previousStatus);
                totalRefund[0] = totalRefund[0].add(refundForTicket);

                BerthReleaseResult release = inventoryService.releaseBookedSlot(
                        key, previousStatus, ticket.getTicketId(), coachCode, berthNumber
                );

                CascadeUpgradeResult cascade = null;
                if (previousStatus == TicketStatus.CONFIRMED && release.freedFullBerth()) {
                    cascade = waitlistService.handleConfirmedCancellation(key, coachCode, berthNumber);
                } else if (previousStatus == TicketStatus.RAC) {
                    cascade = waitlistService.handleRacCancellation(key, ticket.getTicketId());
                } else if (previousStatus == TicketStatus.WAITLISTED) {
                    cascade = waitlistService.handleWaitlistCancellation(key, ticket.getTicketId());
                }

                if (cascade != null) {
                    applyCascadeToTickets(key, cascade, upgradeDescriptions);
                }
            }
        });

        RefundReceipt refundReceipt = paymentService.triggerRefundCompensation(new RefundCommand(
                pnr.getPaymentId(), pnrNumber, totalRefund[0], effectiveReason
        ));
        pnr.setPaymentStatus(refundReceipt.status());
        pnr.setStatusMessage("Cancelled & " + (totalRefund[0].isPositive() ? "refunded " + totalRefund[0] : "no refund per quota rule"));
        telemetry.publish("PNR_CANCELLED", "PNR " + pnrNumber + " upgrades=" + upgradeDescriptions.size());

        boolean valid = waitlistService.verifyContiguousInvariant(key);
        return new CancellationResponse(
                pnrNumber,
                TicketStatus.CANCELLED,
                totalRefund[0],
                refundReceipt.refundId(),
                upgradeDescriptions,
                valid,
                pnr.getStatusMessage()
        );
    }

    private void applyCascadeToTickets(PartitionKey key, CascadeUpgradeResult cascade, List<String> logOut) {
        if (cascade.promotedToConfirmed() != null) {
            WaitlistEntrySnapshot racWinner = cascade.promotedToConfirmed();
            boolean assigned = inventoryService.assignFreedBerthToRacUpgrade(
                    key, cascade.freedCoachForRacWinner(), cascade.freedBerthForRacWinner(), racWinner.ticketId()
            );
            if (assigned) {
                Ticket winnerTicket = ticketsById.get(racWinner.ticketId());
                if (winnerTicket != null) {
                    winnerTicket.upgradeToConfirmed(
                            cascade.freedCoachForRacWinner(),
                            cascade.freedBerthForRacWinner(),
                            "Auto-upgraded from RAC #1 -> CNF (" + cascade.freedCoachForRacWinner() + "-" + cascade.freedBerthForRacWinner() + ")"
                    );
                }
                logOut.add("PNR " + racWinner.pnrNumber() + " (" + racWinner.passengerName() + ") upgraded RAC #1 -> CNF "
                        + cascade.freedCoachForRacWinner() + "-" + cascade.freedBerthForRacWinner());
            }
        }

        if (cascade.promotedToRac() != null) {
            WaitlistEntrySnapshot wlWinner = cascade.promotedToRac();
            boolean assigned = inventoryService.assignRacSlotToWaitlistUpgrade(
                    key, wlWinner.ticketId(), cascade.vacatedRacSlotRef()
            );
            if (assigned) {
                Ticket wlTicket = ticketsById.get(wlWinner.ticketId());
                if (wlTicket != null) {
                    wlTicket.upgradeToRac(
                            wlWinner.allocatedCoach(),
                            wlWinner.allocatedBerth() == null ? 7 : wlWinner.allocatedBerth(),
                            wlWinner.contiguousPosition(),
                            "Auto-upgraded from WL #1 -> RAC #" + wlWinner.contiguousPosition()
                    );
                }
                logOut.add("PNR " + wlWinner.pnrNumber() + " (" + wlWinner.passengerName() + ") upgraded WL #1 -> RAC #"
                        + wlWinner.contiguousPosition());
            }
        }

        for (ShiftedEntry shift : cascade.shiftedEntries()) {
            Ticket shiftedTicket = ticketsById.get(shift.ticketId());
            if (shiftedTicket != null) {
                shiftedTicket.updateContiguousPosition(shift.newPosition());
            }
        }
    }

    @Override
    public ChartFinalizeResponse prepareTrainChart(String trainNumber, LocalDate journeyDate) {
        ChartSummary summary = inventoryService.prepareChart(trainNumber, journeyDate);
        int droppedWl = 0;
        int refundsTriggered = 0;

        for (BookingPnr pnr : pnrsByNumber.values()) {
            PartitionKey key = pnr.getPartitionKey();
            if (key.trainNumber().equals(trainNumber) && key.journeyDate().equals(journeyDate)) {
                boolean hadWaitlisted = false;
                for (Ticket t : pnr.getTickets()) {
                    if (t.getStatus() == TicketStatus.CONFIRMED || t.getStatus() == TicketStatus.RAC) {
                        t.finalizeChart();
                    } else if (t.getStatus() == TicketStatus.WAITLISTED) {
                        t.cancel("Auto-cancelled at final chart preparation");
                        droppedWl++;
                        hadWaitlisted = true;
                    }
                }
                if (hadWaitlisted) {
                    RefundReceipt receipt = paymentService.triggerRefundCompensation(new RefundCommand(
                            pnr.getPaymentId(), pnr.getPnrNumber(), pnr.getTotalFare(),
                            "Full refund: Waitlisted at chart preparation"
                    ));
                    pnr.setPaymentStatus(receipt.status());
                    pnr.setStatusMessage("Auto-cancelled & refunded at chart preparation.");
                    refundsTriggered++;
                } else if (pnr.aggregateStatus() == TicketStatus.CHARTED) {
                    pnr.setStatusMessage("Charted for travel.");
                }
            }
        }
        return new ChartFinalizeResponse(
                summary.trainNumber(), summary.journeyDate(),
                summary.confirmedCount(), summary.racCount(),
                droppedWl, refundsTriggered, summary.preparedAt()
        );
    }

    @Override
    public TatkalSimulationReport runTatkalSurgeSimulation(TatkalSimulationRequest req) {
        LocalDate simDate = timeProvider.currentDate().plusDays(10);
        inventoryService.ensureSimulationJourney(
                req.trainNumber(), simDate, req.cnfCapacity(), req.racCapacity(), req.maxWlCapacity()
        );

        int total = Math.max(10, Math.min(3000, req.totalRequests()));
        int threads = Math.max(4, Math.min(256, req.concurrentThreads()));
        ExecutorService burstPool = Executors.newFixedThreadPool(threads);
        CountDownLatch readyGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(total);

        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger shed503 = new AtomicInteger();
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger rac = new AtomicInteger();
        AtomicInteger waitlisted = new AtomicInteger();
        AtomicInteger soldOutOrError = new AtomicInteger();

        long startNs = System.nanoTime();
        for (int i = 0; i < total; i++) {
            final int idx = i;
            burstPool.submit(() -> {
                try {
                    readyGate.await();
                    BookingRequest bookingReq = BookingRequestBuilder.newBooking()
                            .withUser("tatkal-sim-" + idx)
                            .withTrain(req.trainNumber(), simDate)
                            .withRoute("SRC", "DST")
                            .withClassAndQuota(req.coachClass(), req.quotaCode())
                            .addPassenger("TatkalPax-" + idx, 28, Gender.MALE, BerthPreference.NONE)
                            .withIdempotencyKey("sim-" + simDate + "-" + System.nanoTime() + "-" + idx)
                            .bypassTatkalClock(true)
                            .bypassRateLimit(true)
                            .build();

                    BookingResponse resp = req.strictBackpressureCap()
                            ? admissionGate.executeWithBoundedCap(16, () -> executeBookingCore(bookingReq))
                            : createBooking(bookingReq);

                    admitted.incrementAndGet();
                    switch (resp.status()) {
                        case CONFIRMED -> confirmed.incrementAndGet();
                        case RAC -> rac.incrementAndGet();
                        case WAITLISTED -> waitlisted.incrementAndGet();
                        default -> soldOutOrError.incrementAndGet();
                    }
                } catch (SystemBusyException busy) {
                    shed503.incrementAndGet();
                } catch (Exception ex) {
                    admitted.incrementAndGet();
                    soldOutOrError.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyGate.countDown();
        try {
            doneLatch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            burstPool.shutdownNow();
        }
        long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);

        // Verify zero physical double-bookings across the simulated coach layout
        List<BerthSnapshot> layout = inventoryService.getCoachLayout(req.trainNumber(), simDate, req.coachClass());
        int doubleBooked = 0;
        Map<String, Integer> occupantOccurrences = new HashMap<>();
        for (BerthSnapshot b : layout) {
            int count = b.occupantTicketIds().size();
            boolean sl = "SL".equals(b.berthType());
            if ((!sl && count > 1) || (sl && count > 2)) {
                doubleBooked++;
            }
            for (String tid : b.occupantTicketIds()) {
                occupantOccurrences.merge(tid, 1, Integer::sum);
            }
        }
        for (Integer seen : occupantOccurrences.values()) {
            if (seen > 1) {
                doubleBooked++;
            }
        }

        PartitionKey simKey = new PartitionKey(req.trainNumber(), simDate, req.coachClass(), req.quotaCode());
        boolean contiguousValid = waitlistService.verifyContiguousInvariant(simKey);
        double rps = (total * 1000.0) / elapsedMs;

        return new TatkalSimulationReport(
                req.trainNumber(), simDate, total,
                admitted.get(), shed503.get(), confirmed.get(), rac.get(), waitlisted.get(),
                soldOutOrError.get(), doubleBooked, contiguousValid, elapsedMs, Math.round(rps * 100.0) / 100.0
        );
    }

    @Override
    public AdmissionTelemetry getAdmissionTelemetry() {
        return admissionGate.telemetry();
    }

    private BookingResponse toResponse(BookingPnr pnr, boolean replay) {
        List<TicketItemView> views = pnr.getTickets().stream()
                .map(t -> new TicketItemView(
                        t.getTicketId(),
                        t.getPassengerName(),
                        t.getAge(),
                        t.getStatus(),
                        t.getAllocatedCoach(),
                        t.getAllocatedBerth(),
                        t.getBerthType(),
                        t.getQueuePosition()
                ))
                .toList();

        return new BookingResponse(
                pnr.getPnrNumber(),
                pnr.getPartitionKey().trainNumber(),
                pnr.getPartitionKey().journeyDate(),
                pnr.getSourceStation(),
                pnr.getDestinationStation(),
                pnr.getPartitionKey().coachClass(),
                pnr.getPartitionKey().quotaCode(),
                pnr.aggregateStatus(),
                pnr.getTotalFare(),
                pnr.getPaymentId(),
                pnr.getPaymentStatus(),
                pnr.getIdempotencyKey(),
                replay,
                pnr.getStatusMessage(),
                views,
                pnr.getCreatedAt()
        );
    }
}
