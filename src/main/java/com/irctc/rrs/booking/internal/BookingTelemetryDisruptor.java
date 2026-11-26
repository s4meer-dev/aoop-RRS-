package com.irctc.rrs.booking.internal;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Lock-free LMAX Disruptor ring buffer capturing high-throughput admission & booking events.
 */
@Component
public class BookingTelemetryDisruptor {

    public static class TelemetryEvent {
        private String category;
        private String detail;
        private Instant timestamp;

        public void set(String category, String detail, Instant timestamp) {
            this.category = category;
            this.detail = detail;
            this.timestamp = timestamp;
        }

        public String formatted() {
            return "[" + timestamp + "] " + category + ": " + detail;
        }
    }

    private Disruptor<TelemetryEvent> disruptor;
    private RingBuffer<TelemetryEvent> ringBuffer;
    private final Deque<String> recentFormattedEvents = new ConcurrentLinkedDeque<>();

    @PostConstruct
    public void start() {
        this.disruptor = new Disruptor<>(TelemetryEvent::new, 1024, DaemonThreadFactory.INSTANCE);
        this.disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            recentFormattedEvents.addFirst(event.formatted());
            while (recentFormattedEvents.size() > 80) {
                recentFormattedEvents.pollLast();
            }
        });
        this.ringBuffer = this.disruptor.start();
    }

    @PreDestroy
    public void stop() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }

    public void publish(String category, String detail) {
        if (ringBuffer != null) {
            Instant now = Instant.now();
            ringBuffer.publishEvent((event, seq) -> event.set(category, detail, now));
        }
    }

    public List<String> getRecentEvents(int limit) {
        return recentFormattedEvents.stream().limit(Math.max(1, limit)).toList();
    }
}
