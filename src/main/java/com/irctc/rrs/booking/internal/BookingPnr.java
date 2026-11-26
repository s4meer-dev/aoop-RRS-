package com.irctc.rrs.booking.internal;

import com.irctc.rrs.booking.internal.state.Ticket;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.model.PartitionKey;
import com.irctc.rrs.common.model.TicketStatus;
import com.irctc.rrs.inventory.InventoryService.SeatHoldReceipt;

import java.time.Instant;
import java.util.List;

public class BookingPnr {

    private final String pnrNumber;
    private final String userId;
    private final PartitionKey partitionKey;
    private final String sourceStation;
    private final String destinationStation;
    private final Money totalFare;
    private final String idempotencyKey;
    private final List<Ticket> tickets;
    private final Instant createdAt;

    private volatile String paymentId;
    private volatile String paymentStatus;
    private volatile List<SeatHoldReceipt> pendingHolds;
    private volatile String statusMessage;

    public BookingPnr(
            String pnrNumber,
            String userId,
            PartitionKey partitionKey,
            String sourceStation,
            String destinationStation,
            Money totalFare,
            String idempotencyKey,
            List<Ticket> tickets,
            List<SeatHoldReceipt> pendingHolds,
            Instant createdAt
    ) {
        this.pnrNumber = pnrNumber;
        this.userId = userId;
        this.partitionKey = partitionKey;
        this.sourceStation = sourceStation;
        this.destinationStation = destinationStation;
        this.totalFare = totalFare;
        this.idempotencyKey = idempotencyKey;
        this.tickets = List.copyOf(tickets);
        this.pendingHolds = List.copyOf(pendingHolds);
        this.createdAt = createdAt;
        this.statusMessage = "Booking initiated";
    }

    public String getPnrNumber() {
        return pnrNumber;
    }

    public String getUserId() {
        return userId;
    }

    public PartitionKey getPartitionKey() {
        return partitionKey;
    }

    public String getSourceStation() {
        return sourceStation;
    }

    public String getDestinationStation() {
        return destinationStation;
    }

    public Money getTotalFare() {
        return totalFare;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public List<SeatHoldReceipt> getPendingHolds() {
        return pendingHolds;
    }

    public void clearPendingHolds() {
        this.pendingHolds = List.of();
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public TicketStatus aggregateStatus() {
        if (tickets.isEmpty()) {
            return TicketStatus.EXPIRED;
        }
        return tickets.get(0).getStatus();
    }
}
