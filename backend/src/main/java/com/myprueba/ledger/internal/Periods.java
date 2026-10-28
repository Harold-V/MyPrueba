package com.myprueba.ledger.internal;

import com.myprueba.shared.ValidationException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** Resolves named periods in the business time zone. Weeks run Monday to Sunday. */
final class Periods {

    static final long MAX_RANGE_DAYS = 366;

    private Periods() {}

    static Period resolve(String period, String from, String to, ZoneId zone, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        String name = period == null || period.isBlank() ? "today" : period.strip();
        return switch (name) {
            case "today" -> of(today, today, zone);
            case "week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield of(monday, monday.plusDays(6), zone);
            }
            case "month" -> of(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()), zone);
            case "range" -> range(from, to, zone);
            default -> throw ValidationException.of("period", "invalid_value");
        };
    }

    static boolean isFutureDate(Instant instant, ZoneId zone, Instant now) {
        return LocalDate.ofInstant(instant, zone).isAfter(LocalDate.ofInstant(now, zone));
    }

    private static Period range(String rawFrom, String rawTo, ZoneId zone) {
        LocalDate from = date("from", rawFrom);
        LocalDate to = date("to", rawTo);
        if (to.isBefore(from)) {
            throw ValidationException.of("to", "before_from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw ValidationException.of("to", "range_too_long");
        }
        return of(from, to, zone);
    }

    private static LocalDate date(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw ValidationException.of(field, "required");
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw ValidationException.of(field, "invalid_format");
        }
    }

    private static Period of(LocalDate from, LocalDate to, ZoneId zone) {
        return new Period(
                from,
                to,
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
