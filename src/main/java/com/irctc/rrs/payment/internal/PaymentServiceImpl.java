package com.irctc.rrs.payment.internal;

import com.irctc.rrs.common.events.PaymentReconciledEvent;
import com.irctc.rrs.common.events.RefundTriggeredEvent;
import com.irctc.rrs.common.exception.PaymentConflictException;
import com.irctc.rrs.common.id.IdGenerator;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.payment.PaymentService;
import com.irctc.rrs.payment.internal.SimulatedBankGateway.GatewayOutcome;
import com.irctc.rrs.payment.internal.SimulatedBankGateway.GatewayResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final SimulatedBankGateway bankGateway;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, PaymentTransaction> idempotencyLedger = new ConcurrentHashMap<>();
    private final Map<String, PaymentTransaction> byPnr = new ConcurrentHashMap<>();
    private final Map<String, RefundReceipt> refundLedgerByKey = new ConcurrentHashMap<>();
    private final Deque<PaymentTransaction> recentTransactions = new ConcurrentLinkedDeque<>();
    private final Deque<RefundReceipt> recentRefunds = new ConcurrentLinkedDeque<>();

    private final Counter succeededCounter;
    private final Counter replayCounter;
    private final Counter timeoutCounter;
    private final Counter reconciledCounter;
    private final Counter refundCounter;

    public PaymentServiceImpl(
            SimulatedBankGateway bankGateway,
            IdGenerator idGenerator,
            TimeProvider timeProvider,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.bankGateway = bankGateway;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
        this.eventPublisher = eventPublisher;

        this.succeededCounter = Counter.builder("rrs.payments.succeeded").register(meterRegistry);
        this.replayCounter = Counter.builder("rrs.payments.idempotent_replays").register(meterRegistry);
        this.timeoutCounter = Counter.builder("rrs.payments.timeouts").register(meterRegistry);
        this.reconciledCounter = Counter.builder("rrs.payments.reconciled").register(meterRegistry);
        this.refundCounter = Counter.builder("rrs.payments.auto_refunds").register(meterRegistry);
    }

    @Override
    public PaymentResult processPayment(PaymentCommand command) {
        Instant now = timeProvider.now();
        PaymentMode mode = command.mode() == null ? PaymentMode.NORMAL_SUCCESS : command.mode();

        boolean[] createdNew = {false};
        PaymentTransaction txn = idempotencyLedger.computeIfAbsent(command.idempotencyKey(), key -> {
            createdNew[0] = true;
            return new PaymentTransaction(
                    idGenerator.nextId("PAY"),
                    command.pnrNumber(),
                    key,
                    command.amount(),
                    mode,
                    now
            );
        });

        synchronized (txn) {
            if (!createdNew[0]) {
                if (!txn.getAmount().equals(command.amount())) {
                    throw new PaymentConflictException(
                            "Idempotency-Key " + command.idempotencyKey() + " already bound to different amount " + txn.getAmount());
                }
                replayCounter.increment();
                return txn.toResult(true);
            }

            byPnr.put(command.pnrNumber(), txn);
            recentTransactions.addFirst(txn);
            trimDeque(recentTransactions, 200);

            txn.markProcessing(timeProvider.now());
            GatewayResponse response = bankGateway.charge(command.idempotencyKey(), command.amount(), mode);

            switch (response.outcome()) {
                case APPROVED -> {
                    txn.markSucceeded(response.gatewayReference(), response.detail(), timeProvider.now());
                    succeededCounter.increment();
                }
                case DECLINED -> txn.markFailed(response.gatewayReference(), response.detail(), timeProvider.now());
                case TIMEOUT_UNKNOWN -> {
                    txn.markTimeoutPending(response.gatewayReference(), response.detail(), timeProvider.now());
                    timeoutCounter.increment();
                }
            }
            return txn.toResult(false);
        }
    }

    @Override
    public RefundReceipt triggerRefundCompensation(RefundCommand command) {
        if (command.refundAmount() == null || !command.refundAmount().isPositive()) {
            return new RefundReceipt(
                    idGenerator.nextId("REF"),
                    command.paymentId(),
                    command.pnrNumber(),
                    Money.ZERO,
                    command.reason(),
                    "NO_REFUND_APPLICABLE",
                    timeProvider.now()
            );
        }
        String refundDedupKey = command.paymentId() + "::" + command.reason();
        return refundLedgerByKey.computeIfAbsent(refundDedupKey, ignored -> {
            bankGateway.executeRefund(command.paymentId(), command.refundAmount());
            Instant now = timeProvider.now();
            RefundReceipt receipt = new RefundReceipt(
                    idGenerator.nextId("REF"),
                    command.paymentId(),
                    command.pnrNumber(),
                    command.refundAmount(),
                    command.reason(),
                    "REFUNDED",
                    now
            );
            recentRefunds.addFirst(receipt);
            trimDeque(recentRefunds, 200);
            refundCounter.increment();

            PaymentTransaction txn = byPnr.get(command.pnrNumber());
            if (txn != null) {
                synchronized (txn) {
                    txn.markRefunded("Compensated via " + receipt.refundId() + ": " + command.reason(), now);
                }
            }
            eventPublisher.publishEvent(new RefundTriggeredEvent(
                    receipt.refundId(), command.paymentId(), command.pnrNumber(), command.refundAmount(), command.reason(), now
            ));
            return receipt;
        });
    }

    @Override
    @Scheduled(fixedDelayString = "${rrs.payment.reconciliation-interval-ms:5000}")
    public List<PaymentResult> reconcilePendingPayments() {
        List<PaymentResult> reconciled = new ArrayList<>();
        for (PaymentTransaction txn : idempotencyLedger.values()) {
            if (txn.getStatus() == PaymentStatus.TIMEOUT_PENDING) {
                synchronized (txn) {
                    if (txn.getStatus() == PaymentStatus.TIMEOUT_PENDING) {
                        GatewayOutcome bankTruth = bankGateway.queryStatus(txn.getIdempotencyKey());
                        Instant now = timeProvider.now();
                        boolean approved = (bankTruth == GatewayOutcome.APPROVED);
                        if (approved) {
                            txn.markReconciledSuccess(txn.getGatewayReference(),
                                    "Reconciled: Bank confirmed debit", now);
                        } else {
                            txn.markReconciledFailed(txn.getGatewayReference(),
                                    "Reconciled: Bank confirmed no debit occurred", now);
                        }
                        reconciledCounter.increment();
                        reconciled.add(txn.toResult(false));

                        eventPublisher.publishEvent(new PaymentReconciledEvent(
                                txn.getPaymentId(), txn.getPnrNumber(), txn.getIdempotencyKey(), approved, now
                        ));
                    }
                }
            }
        }
        return reconciled;
    }

    @Override
    public Optional<PaymentResult> getPaymentByPnr(String pnrNumber) {
        PaymentTransaction txn = byPnr.get(pnrNumber);
        if (txn == null) {
            return Optional.empty();
        }
        synchronized (txn) {
            return Optional.of(txn.toResult(false));
        }
    }

    @Override
    public List<PaymentResult> listRecentTransactions(int limit) {
        return recentTransactions.stream().limit(Math.max(1, limit)).map(t -> {
            synchronized (t) {
                return t.toResult(false);
            }
        }).toList();
    }

    @Override
    public List<RefundReceipt> listRecentRefunds(int limit) {
        return recentRefunds.stream().limit(Math.max(1, limit)).toList();
    }

    private <T> void trimDeque(Deque<T> deque, int max) {
        while (deque.size() > max) {
            deque.pollLast();
        }
    }
}
