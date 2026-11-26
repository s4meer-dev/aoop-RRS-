package com.irctc.rrs.common.concurrency;

import com.irctc.rrs.common.model.PartitionKey;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Fine-grained partition lock manager ensuring thread-safe seat & queue operations
 * per (Train, JourneyDate, CoachClass, QuotaCode) slice without global database locks.
 */
@Component
public class PartitionLockManager {

    private final ConcurrentHashMap<PartitionKey, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T executeWithLock(PartitionKey key, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock(true));
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void runWithLock(PartitionKey key, Runnable action) {
        executeWithLock(key, () -> {
            action.run();
            return null;
        });
    }

    public int activePartitionCount() {
        return locks.size();
    }
}
