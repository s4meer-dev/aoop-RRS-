package com.irctc.rrs.booking;

import com.irctc.rrs.common.model.CoachClass;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.QuotaCode;
import com.irctc.rrs.common.model.TicketStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Public API of the Booking Bounded Context (Unit 7 + Unit 6).
 */
public interface BookingService {

    BookingResponse createBooking(BookingRequest request);

    BookingResponse getBooking(String pnrNumber);

    List<BookingResponse> listBookings(int limit);

    CancellationResponse cancelBooking(String pnrNumber, String reason);

    ChartFinalizeResponse prepareTrainChart(String trainNumber, LocalDate journeyDate);

    TatkalSimulationReport runTatkalSurgeSimulation(TatkalSimulationRequest request);

    AdmissionTelemetry getAdmissionTelemetry();

    record TicketItemView(
            String ticketId,
            String passengerName,
            int age,
            TicketStatus status,
            String allocatedCoach,
            Integer allocatedBerth,
            String berthType,
            int queuePosition
    ) {}

    record BookingResponse(
            String pnrNumber,
            String trainNumber,
            LocalDate journeyDate,
            String sourceStation,
            String destinationStation,
            CoachClass coachClass,
            QuotaCode quotaCode,
            TicketStatus status,
            Money totalFare,
            String paymentId,
            String paymentStatus,
            String idempotencyKey,
            boolean idempotentReplay,
            String message,
            List<TicketItemView> tickets,
            Instant createdAt
    ) {}

    record CancellationResponse(
            String pnrNumber,
            TicketStatus status,
            Money refundAmount,
            String refundId,
            List<String> cascadeUpgrades,
            boolean queueContiguousValid,
            String message
    ) {}

    record ChartFinalizeResponse(
            String trainNumber,
            LocalDate journeyDate,
            int confirmedCount,
            int racCount,
            int droppedWaitlistCount,
            int autoRefundsTriggered,
            Instant preparedAt
    ) {}

    record TatkalSimulationRequest(
            String trainNumber,
            CoachClass coachClass,
            QuotaCode quotaCode,
            int totalRequests,
            int concurrentThreads,
            int cnfCapacity,
            int racCapacity,
            int maxWlCapacity,
            boolean strictBackpressureCap
    ) {}

    record TatkalSimulationReport(
            String trainNumber,
            LocalDate simulationDate,
            int totalRequests,
            int admittedCount,
            int shedByBackpressure503,
            int confirmedBookings,
            int racBookings,
            int waitlistedBookings,
            int rejectedSoldOutOrError,
            int doubleBookedBerthsDetected,
            boolean waitlistContiguousInvariantValid,
            long elapsedMs,
            double requestsPerSecond
    ) {}

    record AdmissionTelemetry(
            int workerThreads,
            int boundedQueueCapacity,
            int currentQueueDepth,
            long totalAdmitted,
            long totalShed503,
            long totalRateLimited429,
            List<String> recentEvents
    ) {}
}
