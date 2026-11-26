package com.irctc.rrs.common.id;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DefaultIdGenerator implements IdGenerator {

    private final AtomicLong pnrSequence = new AtomicLong(4200000000L);

    @Override
    public String nextPnr() {
        return String.valueOf(pnrSequence.incrementAndGet());
    }

    @Override
    public String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
