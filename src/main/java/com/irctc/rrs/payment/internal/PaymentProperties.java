package com.irctc.rrs.payment.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rrs.payment")
public class PaymentProperties {

    private long reconciliationIntervalMs = 5000;
    private long gatewayTimeoutMs = 400;

    public long getReconciliationIntervalMs() {
        return reconciliationIntervalMs;
    }

    public void setReconciliationIntervalMs(long reconciliationIntervalMs) {
        this.reconciliationIntervalMs = reconciliationIntervalMs;
    }

    public long getGatewayTimeoutMs() {
        return gatewayTimeoutMs;
    }

    public void setGatewayTimeoutMs(long gatewayTimeoutMs) {
        this.gatewayTimeoutMs = gatewayTimeoutMs;
    }
}
