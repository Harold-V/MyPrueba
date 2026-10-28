package com.myprueba.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;

/** Local dates {@code from..to} (inclusive) and the matching instants {@code [start, end)}. */
record Period(LocalDate from, LocalDate to, Instant start, Instant end) {

    boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
