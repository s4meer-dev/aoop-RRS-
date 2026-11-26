package com.irctc.rrs.payment.internal;

import com.irctc.rrs.common.id.IdGenerator;
import com.irctc.rrs.common.model.Money;
import com.irctc.rrs.common.time.TimeProvider;
import com.irctc.rrs.payment.PaymentService.PaymentMode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Abstracted bank gateway protected by Resilience4j Retry & CircuitBreaker,
 * supporting deterministic simulation of bank timeouts and late post-timeout debits.
 */
@Component
public class SimulatedBankGateway {

    public enum GatewayOutcome {
        APPROVED,
        DECLINED,
        TIMEOUT_UNKNOWN
    }

    public record GatewayResponse(
            GatewayOutcome outcome,
            String gatewayReference,
            String detail
    ) {}

    private final IdGenerator idGenerator;
    private final Map<String, GatewayOutcome> delayedBankTruthByKey = new ConcurrentHashMap<>();
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public SimulatedBankGateway(IdGenerator idGenerator, TimeProvider timeProvider) {
        this.idGenerator = idGenerator;
        this.circuitBreaker = CircuitBreaker.of("bankGateway", CircuitBreakerConfig.custom()
                .failureRateThreshold(75.0f)
                .slidingWindowSize(20)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .build());
        this.retry = Retry.of("bankGatewayRetry", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(15))
                .build());
    }

    public GatewayResponse charge(String idempotencyKey, Money amount, PaymentMode mode) {
        Supplier<GatewayResponse> call = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, () -> executeCharge(idempotencyKey, amount, mode)));
        return call.get();
    }

    public GatewayOutcome queryStatus(String idempotencyKey) {
        return delayedBankTruthByKey.getOrDefault(idempotencyKey, GatewayOutcome.DECLINED);
    }

    public String executeRefund(String paymentId, Money refundAmount) {
        return idGenerator.nextId("BANK-REF");
    }

    private GatewayResponse executeCharge(String idempotencyKey, Money amount, PaymentMode mode) {
        String ref = idGenerator.nextId("GW");
        return switch (mode) {
            case NORMAL_SUCCESS -> {
                delayedBankTruthByKey.put(idempotencyKey, GatewayOutcome.APPROVED);
                yield new GatewayResponse(GatewayOutcome.APPROVED, ref, "Bank debit confirmed for " + amount);
            }
            case DECLINE_INSUFFICIENT_FUNDS -> {
                delayedBankTruthByKey.put(idempotencyKey, GatewayOutcome.DECLINED);
                yield new GatewayResponse(GatewayOutcome.DECLINED, ref, "Declined by issuing bank (Insufficient funds)");
            }
            case GATEWAY_TIMEOUT_THEN_SUCCESS, SLOW_SUCCESS_AFTER_HOLD_EXPIRY -> {
                // Bank actually debited money slowly even though sync HTTP call timed out
                delayedBankTruthByKey.put(idempotencyKey, GatewayOutcome.APPROVED);
                yield new GatewayResponse(GatewayOutcome.TIMEOUT_UNKNOWN, ref, "Gateway timeout; awaiting reconciliation");
            }
            case GATEWAY_TIMEOUT_THEN_FAILED -> {
                // Bank timed out and never debited the customer's account
                delayedBankTruthByKey.put(idempotencyKey, GatewayOutcome.DECLINED);
                yield new GatewayResponse(GatewayOutcome.TIMEOUT_UNKNOWN, ref, "Gateway timeout; awaiting reconciliation");
            }
        };
    }
}
