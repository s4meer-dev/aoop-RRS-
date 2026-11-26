package com.irctc.rrs.payment;

import com.irctc.rrs.common.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public API of the Payment Bounded Context (Unit 5 & Unit 7).
 */
public interface PaymentService {

    enum PaymentMode {
        NORMAL_SUCCESS,
        DECLINE_INSUFFICIENT_FUNDS,
        GATEWAY_TIMEOUT_THEN_SUCCESS,
        GATEWAY_TIMEOUT_THEN_FAILED,
        SLOW_SUCCESS_AFTER_HOLD_EXPIRY
    }

    enum PaymentStatus {
        CREATED,
        PROCESSING,
        SUCCEEDED,
        FAILED,
        TIMEOUT_PENDING,
        RECONCILED_SUCCESS,
        RECONCILED_FAILED,
        REFUND_INITIATED,
        REFUNDED
    }

    PaymentResult processPayment(PaymentCommand command);

    RefundReceipt triggerRefundCompensation(RefundCommand command);

    List<PaymentResult> reconcilePendingPayments();

    Optional<PaymentResult> getPaymentByPnr(String pnrNumber);

    List<PaymentResult> listRecentTransactions(int limit);

    List<RefundReceipt> listRecentRefunds(int limit);

    record PaymentCommand(
            String pnrNumber,
            String idempotencyKey,
            Money amount,
            PaymentMode mode
    ) {}

    record PaymentResult(
            String paymentId,
            String pnrNumber,
            String idempotencyKey,
            Money amount,
            PaymentStatus status,
            String gatewayReference,
            boolean idempotentReplay,
            String message,
            Instant updatedAt
    ) {}

    record RefundCommand(
            String paymentId,
            String pnrNumber,
            Money refundAmount,
            String reason
    ) {}

    record RefundReceipt(
            String refundId,
            String paymentId,
            String pnrNumber,
            Money refundAmount,
            String reason,
            String status,
            Instant processedAt
    ) {}
}
