package com.myprueba.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move. Starts at the real current instant. */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void set(Instant instant) {
        this.now = instant;
    }

    public void reset() {
        this.now = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
