package com.irctc.rrs.payment.internal;

import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.payment.PaymentService.PaymentMode;
import com.irctc.rrs.payment.PaymentService.PaymentResult;
import com.irctc.rrs.payment.PaymentService.PaymentStatus;

import java.time.Instant;

public class PaymentTransaction {

    private final String paymentId;
    private final String pnrNumber;
    private final String idempotencyKey;
    private final Money amount;
    private final PaymentMode mode;

    private PaymentStatus status;
    private String gatewayReference;
    private String message;
    private Instant updatedAt;

    public PaymentTransaction(
            String paymentId,
            String pnrNumber,
            String idempotencyKey,
            Money amount,
            PaymentMode mode,
            Instant now
    ) {
        this.paymentId = paymentId;
        this.pnrNumber = pnrNumber;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.mode = mode;
        this.status = PaymentStatus.CREATED;
        this.message = "Payment intent initialized";
        this.updatedAt = now;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getPnrNumber() {
        return pnrNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentMode getMode() {
        return mode;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public String getMessage() {
        return message;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markProcessing(Instant now) {
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void markSucceeded(String gatewayRef, String message, Instant now) {
        this.status = PaymentStatus.SUCCEEDED;
        this.gatewayReference = gatewayRef;
        this.message = message;
        this.updatedAt = now;
    }

    public void markFailed(String gatewayRef, String message, Instant now) {
        this.status = PaymentStatus.FAILED;
        this.gatewayReference = gatewayRef;
        this.message = message;
        this.updatedAt = now;
    }

    public void markTimeoutPending(String gatewayRef, String message, Instant now) {
        this.status = PaymentStatus.TIMEOUT_PENDING;
        this.gatewayReference = gatewayRef;
        this.message = message;
        this.updatedAt = now;
    }

    public void markReconciledSuccess(String gatewayRef, String message, Instant now) {
        this.status = PaymentStatus.RECONCILED_SUCCESS;
        this.gatewayReference = gatewayRef;
        this.message = message;
        this.updatedAt = now;
    }

    public void markReconciledFailed(String gatewayRef, String message, Instant now) {
        this.status = PaymentStatus.RECONCILED_FAILED;
        this.gatewayReference = gatewayRef;
        this.message = message;
        this.updatedAt = now;
    }

    public void markRefunded(String message, Instant now) {
        this.status = PaymentStatus.REFUNDED;
        this.message = message;
        this.updatedAt = now;
    }

    public PaymentResult toResult(boolean replay) {
        return new PaymentResult(
                paymentId, pnrNumber, idempotencyKey, amount, status, gatewayReference, replay, message, updatedAt
        );
    }
}
