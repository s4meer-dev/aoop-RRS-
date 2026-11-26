package com.irctc.rrs.booking.internal;

import com.irctc.rrs.booking.BookingService.AdmissionTelemetry;
import com.irctc.rrs.common.exception.RateLimitExceededException;
import com.irctc.rrs.common.exception.RrsException;
import com.irctc.rrs.common.exception.SystemBusyException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Unit 6: 10 AM Rush Backpressure Admission Gate.
 * Combines Bucket4j token-bucket rate limiting, a bounded FIFO queue + fixed worker pool,
 * Resilience4j Bulkhead isolation, and LMAX Disruptor telemetry.
 */
@Component
public class TatkalAdmissionGate {

    private final AdmissionProperties properties;
    private final BookingTelemetryDisruptor telemetryDisruptor;
    private final ArrayBlockingQueue<Runnable> boundedQueue;
    private final ThreadPoolExecutor workerPool;
    private final Bulkhead bulkhead;
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    private final AtomicLong totalAdmitted = new AtomicLong();
    private final AtomicLong totalShed503 = new AtomicLong();
    private final AtomicLong totalRateLimited429 = new AtomicLong();

    private final Counter admittedCounter;
    private final Counter shed503Counter;
    private final Counter rateLimited429Counter;

    public TatkalAdmissionGate(
            AdmissionProperties properties,
            BookingTelemetryDisruptor telemetryDisruptor,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.telemetryDisruptor = telemetryDisruptor;
        this.boundedQueue = new ArrayBlockingQueue<>(Math.max(4, properties.getBoundedQueueCapacity()));

        AtomicInteger threadIndex = new AtomicInteger(1);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "tatkal-worker-" + threadIndex.getAndIncrement());
            t.setDaemon(true);
            return t;
        };

        int workers = Math.max(2, properties.getWorkerThreads());
        this.workerPool = new ThreadPoolExecutor(
                workers,
                workers,
                30L,
                TimeUnit.SECONDS,
                this.boundedQueue,
                tf,
                new ThreadPoolExecutor.AbortPolicy()
        );

        this.bulkhead = Bulkhead.of("tatkalAdmissionBulkhead", BulkheadConfig.custom()
                .maxConcurrentCalls(Math.max(workers, properties.getBulkheadMaxConcurrentCalls()))
                .maxWaitDuration(Duration.ofMillis(25))
                .build());

        Gauge.builder("rrs.admission.queue.depth", boundedQueue, ArrayBlockingQueue::size)
                .register(meterRegistry);
        this.admittedCounter = Counter.builder("rrs.admission.admitted").register(meterRegistry);
        this.shed503Counter = Counter.builder("rrs.admission.shed_503").register(meterRegistry);
        this.rateLimited429Counter = Counter.builder("rrs.admission.rate_limited_429").register(meterRegistry);
    }

    public <T> T admitAndExecute(String userId, boolean bypassRateLimit, Supplier<T> task) {
        if (!bypassRateLimit) {
            Bucket bucket = userBuckets.computeIfAbsent(userId, ignored -> createUserBucket());
            if (!bucket.tryConsume(1)) {
                totalRateLimited429.incrementAndGet();
                rateLimited429Counter.increment();
                telemetryDisruptor.publish("RATE_LIMIT_429", "User " + userId + " exceeded token bucket");
                throw new RateLimitExceededException("Too many booking attempts by user " + userId + ". Slow down.");
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            workerPool.execute(() -> {
                try {
                    T result = Bulkhead.decorateSupplier(bulkhead, task).get();
                    future.complete(result);
                } catch (BulkheadFullException bf) {
                    future.completeExceptionally(new SystemBusyException("Bulkhead saturated under Tatkal spike."));
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });
            totalAdmitted.incrementAndGet();
            admittedCounter.increment();
        } catch (RejectedExecutionException rejected) {
            totalShed503.incrementAndGet();
            shed503Counter.increment();
            telemetryDisruptor.publish("BACKPRESSURE_503", "Bounded queue full (" + boundedQueue.size() + ")");
            throw new SystemBusyException("10 AM Tatkal rush backpressure active: server busy, please retry shortly.");
        }

        try {
            return future.get(Math.max(500, properties.getQueueWaitTimeoutMs()), TimeUnit.MILLISECONDS);
        } catch (CompletionException ce) {
            throw unwrap(ce.getCause());
        } catch (Exception ex) {
            if (ex.getCause() != null) {
                throw unwrap(ex.getCause());
            }
            totalShed503.incrementAndGet();
            shed503Counter.increment();
            throw new SystemBusyException("Request timed out in Tatkal queue; server remains healthy.");
        }
    }

    public <T> T executeWithBoundedCap(int customQueueCap, Supplier<T> task) {
        int inFlightAndQueued = workerPool.getActiveCount() + boundedQueue.size();
        if (inFlightAndQueued >= customQueueCap) {
            totalShed503.incrementAndGet();
            shed503Counter.increment();
            telemetryDisruptor.publish("BACKPRESSURE_503", "Simulation backpressure cap (" + customQueueCap + ") reached");
            throw new SystemBusyException("Simulation backpressure cap (" + customQueueCap + ") reached.");
        }
        return admitAndExecute("sim-user", true, task);
    }

    public AdmissionTelemetry telemetry() {
        return new AdmissionTelemetry(
                workerPool.getCorePoolSize(),
                properties.getBoundedQueueCapacity(),
                boundedQueue.size(),
                totalAdmitted.get(),
                totalShed503.get(),
                totalRateLimited429.get(),
                telemetryDisruptor.getRecentEvents(15)
        );
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdownNow();
    }

    private Bucket createUserBucket() {
        int rpm = Math.max(10, properties.getPerUserTokensPerMinute());
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rpm).refillGreedy(rpm, Duration.ofMinutes(1)).build())
                .build();
    }

    private RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RrsException rrs) {
            return rrs;
        }
        if (cause instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(cause);
    }
}
