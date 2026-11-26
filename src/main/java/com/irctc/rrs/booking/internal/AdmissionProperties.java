package com.irctc.rrs.booking.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rrs.admission")
public class AdmissionProperties {

    private int boundedQueueCapacity = 120;
    private int workerThreads = 16;
    private int perUserTokensPerMinute = 60;
    private int bulkheadMaxConcurrentCalls = 32;
    private long queueWaitTimeoutMs = 1500;

    public int getBoundedQueueCapacity() {
        return boundedQueueCapacity;
    }

    public void setBoundedQueueCapacity(int boundedQueueCapacity) {
        this.boundedQueueCapacity = boundedQueueCapacity;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getPerUserTokensPerMinute() {
        return perUserTokensPerMinute;
    }

    public void setPerUserTokensPerMinute(int perUserTokensPerMinute) {
        this.perUserTokensPerMinute = perUserTokensPerMinute;
    }

    public int getBulkheadMaxConcurrentCalls() {
        return bulkheadMaxConcurrentCalls;
    }

    public void setBulkheadMaxConcurrentCalls(int bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
    }

    public long getQueueWaitTimeoutMs() {
        return queueWaitTimeoutMs;
    }

    public void setQueueWaitTimeoutMs(long queueWaitTimeoutMs) {
        this.queueWaitTimeoutMs = queueWaitTimeoutMs;
    }
}
