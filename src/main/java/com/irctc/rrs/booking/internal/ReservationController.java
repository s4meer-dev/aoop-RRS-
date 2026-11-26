package com.irctc.rrs.booking.internal;

import com.irctc.rrs.booking.BookingRequest;
import com.irctc.rrs.booking.BookingRequestBuilder;
import com.irctc.rrs.booking.BookingService;
import com.irctc.rrs.booking.BookingService.AdmissionTelemetry;
import com.irctc.rrs.booking.BookingService.BookingResponse;
import com.irctc.rrs.booking.BookingService.CancellationResponse;
import com.irctc.rrs.booking.BookingService.ChartFinalizeResponse;
import com.irctc.rrs.booking.BookingService.TatkalSimulationReport;
import com.irctc.rrs.booking.BookingService.TatkalSimulationRequest;
import com.irctc.rrs.common.model.BerthPreference;
import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Gender;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.inventory.InventoryService;
import com.irctc.rrs.inventory.InventoryService.AvailabilitySnapshot;
import com.irctc.rrs.inventory.InventoryService.BerthSnapshot;
import com.irctc.rrs.inventory.InventoryService.TrainSummary;
import com.irctc.rrs.payment.PaymentService;
import com.irctc.rrs.payment.PaymentService.PaymentMode;
import com.irctc.rrs.payment.PaymentService.PaymentResult;
import com.irctc.rrs.payment.PaymentService.RefundReceipt;
import com.irctc.rrs.waitlist.WaitlistService;
import com.irctc.rrs.waitlist.WaitlistService.PromotionAuditEntry;
import com.irctc.rrs.waitlist.WaitlistService.QueueSnapshot;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    private final BookingService bookingService;
    private final InventoryService inventoryService;
    private final WaitlistService waitlistService;
    private final PaymentService paymentService;
    private final TimeProvider timeProvider;

    public ReservationController(
            BookingService bookingService,
            InventoryService inventoryService,
            WaitlistService waitlistService,
            PaymentService paymentService,
            TimeProvider timeProvider
    ) {
        this.bookingService = bookingService;
        this.inventoryService = inventoryService;
        this.waitlistService = waitlistService;
        this.paymentService = paymentService;
        this.timeProvider = timeProvider;
    }

    public record CreateBookingApiPayload(
            String userId,
            String trainNumber,
            LocalDate journeyDate,
            String sourceStation,
            String destinationStation,
            CoachClass coachClass,
            QuotaCode quotaCode,
            PaymentMode paymentMode,
            boolean bypassTatkalClock,
            List<PassengerPayload> passengers
    ) {}

    public record PassengerPayload(
            String name,
            int age,
            Gender gender,
            BerthPreference preference
    ) {}

    public record CancelApiPayload(String reason) {}

    @GetMapping("/trains")
    public List<TrainSummary> listTrains() {
        return inventoryService.listTrains();
    }

    @GetMapping("/trains/{trainNumber}/availability")
    public AvailabilitySnapshot getAvailability(
            @PathVariable String trainNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate,
            @RequestParam(defaultValue = "AC_3") CoachClass coachClass,
            @RequestParam(defaultValue = "GN") QuotaCode quotaCode
    ) {
        LocalDate date = journeyDate != null ? journeyDate : timeProvider.currentDate();
        return inventoryService.getAvailability(new PartitionKey(trainNumber, date, coachClass, quotaCode));
    }

    @GetMapping("/trains/{trainNumber}/coaches")
    public List<BerthSnapshot> getCoachLayout(
            @PathVariable String trainNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate,
            @RequestParam(defaultValue = "AC_3") CoachClass coachClass
    ) {
        LocalDate date = journeyDate != null ? journeyDate : timeProvider.currentDate();
        return inventoryService.getCoachLayout(trainNumber, date, coachClass);
    }

    @PostMapping("/bookings")
    public BookingResponse createBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateBookingApiPayload payload
    ) {
        LocalDate date = payload.journeyDate() != null ? payload.journeyDate() : timeProvider.currentDate();
        BookingRequestBuilder builder = BookingRequestBuilder.newBooking()
                .withUser(payload.userId())
                .withTrain(payload.trainNumber(), date)
                .withRoute(payload.sourceStation(), payload.destinationStation())
                .withClassAndQuota(payload.coachClass(), payload.quotaCode())
                .withIdempotencyKey(idempotencyKey)
                .withPaymentMode(payload.paymentMode())
                .bypassTatkalClock(payload.bypassTatkalClock());

        if (payload.passengers() != null) {
            for (PassengerPayload p : payload.passengers()) {
                builder.addPassenger(p.name(), p.age(), p.gender(), p.preference());
            }
        }
        BookingRequest request = builder.build();
        return bookingService.createBooking(request);
    }

    @GetMapping("/bookings")
    public List<BookingResponse> listBookings(@RequestParam(defaultValue = "50") int limit) {
        return bookingService.listBookings(limit);
    }

    @GetMapping("/bookings/{pnrNumber}")
    public BookingResponse getBooking(@PathVariable String pnrNumber) {
        return bookingService.getBooking(pnrNumber);
    }

    @PostMapping("/bookings/{pnrNumber}/cancel")
    public CancellationResponse cancelBooking(
            @PathVariable String pnrNumber,
            @RequestBody(required = false) CancelApiPayload payload
    ) {
        return bookingService.cancelBooking(pnrNumber, payload == null ? "Cancelled by user" : payload.reason());
    }

    @GetMapping("/waitlist/{trainNumber}")
    public QueueSnapshot getWaitlistSnapshot(
            @PathVariable String trainNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate,
            @RequestParam(defaultValue = "AC_3") CoachClass coachClass,
            @RequestParam(defaultValue = "GN") QuotaCode quotaCode
    ) {
        LocalDate date = journeyDate != null ? journeyDate : timeProvider.currentDate();
        return waitlistService.getQueueSnapshot(new PartitionKey(trainNumber, date, coachClass, quotaCode));
    }

    @GetMapping("/waitlist/audit")
    public List<PromotionAuditEntry> getWaitlistAudit(@RequestParam(defaultValue = "30") int limit) {
        return waitlistService.getRecentAuditLog(limit);
    }

    @GetMapping("/payments/transactions")
    public List<PaymentResult> listPayments(@RequestParam(defaultValue = "30") int limit) {
        return paymentService.listRecentTransactions(limit);
    }

    @GetMapping("/payments/refunds")
    public List<RefundReceipt> listRefunds(@RequestParam(defaultValue = "30") int limit) {
        return paymentService.listRecentRefunds(limit);
    }

    @PostMapping("/payments/reconcile")
    public List<PaymentResult> reconcileNow() {
        return paymentService.reconcilePendingPayments();
    }

    @PostMapping("/admin/chart/{trainNumber}")
    public ChartFinalizeResponse prepareChart(
            @PathVariable String trainNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate
    ) {
        LocalDate date = journeyDate != null ? journeyDate : timeProvider.currentDate();
        return bookingService.prepareTrainChart(trainNumber, date);
    }

    @PostMapping("/simulation/tatkal-surge")
    public TatkalSimulationReport runTatkalSurge(@RequestBody(required = false) TatkalSimulationRequest payload) {
        TatkalSimulationRequest req = payload != null ? payload : new TatkalSimulationRequest(
                "12951", CoachClass.AC_3, QuotaCode.TQ, 400, 120, 10, 4, 15, true
        );
        return bookingService.runTatkalSurgeSimulation(req);
    }

    @GetMapping("/telemetry")
    public Map<String, Object> getTelemetry() {
        AdmissionTelemetry admission = bookingService.getAdmissionTelemetry();
        return Map.of(
                "admission", admission,
                "recentPromotions", waitlistService.getRecentAuditLog(10),
                "recentRefunds", paymentService.listRecentRefunds(10)
        );
    }
}
