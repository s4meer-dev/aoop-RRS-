package com.irctc.rrs.common.id;

public interface IdGenerator {

    String nextPnr();

    String nextId(String prefix);
}
