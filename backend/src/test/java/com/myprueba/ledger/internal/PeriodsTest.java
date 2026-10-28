package com.myprueba.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myprueba.shared.FieldViolation;
import com.myprueba.shared.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PeriodsTest {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // Monday 2026-10-26 10:00 in Bogotá (UTC-5)
    static final Instant MONDAY_MORNING = Instant.parse("2026-10-26T15:00:00Z");

    @Test
    void todayIsTheLocalCalendarDay() {
        Period p = Periods.resolve(null, null, null, BOGOTA, MONDAY_MORNING);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(p.start()).isEqualTo(Instant.parse("2026-10-26T05:00:00Z"));
        assertThat(p.end()).isEqualTo(Instant.parse("2026-10-27T05:00:00Z"));
    }

    @Test
    void weekRunsMondayToSunday() {
        Period p = Periods.resolve("week", null, null, BOGOTA, Instant.parse("2026-11-01T20:00:00Z"));
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void sundayLateNightBogotaBelongsToThePreviousWeek() {
        Period week = Periods.resolve("week", null, null, BOGOTA, MONDAY_MORNING);
        Instant sunday2330 = Instant.parse("2026-10-26T04:30:00Z");
        assertThat(week.contains(sunday2330)).isFalse();
        assertThat(week.contains(week.start())).isTrue();
        assertThat(week.contains(week.end())).isFalse();
    }

    @Test
    void monthStartsOnTheFirst() {
        Period p = Periods.resolve("month", null, null, BOGOTA, MONDAY_MORNING);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void rangeIsInclusiveOfBothDates() {
        Period p = Periods.resolve("range", "2026-01-01", "2026-01-31", BOGOTA, MONDAY_MORNING);
        assertThat(p.start()).isEqualTo(Instant.parse("2026-01-01T05:00:00Z"));
        assertThat(p.end()).isEqualTo(Instant.parse("2026-02-01T05:00:00Z"));
    }

    @Test
    void rangeRejectsInvertedOrTooLongOrMissingDates() {
        assertViolation(
                () -> Periods.resolve("range", "2026-02-01", "2026-01-01", BOGOTA, MONDAY_MORNING),
                "to",
                "before_from");
        assertViolation(
                () -> Periods.resolve("range", "2024-01-01", "2026-01-01", BOGOTA, MONDAY_MORNING),
                "to",
                "range_too_long");
        assertViolation(() -> Periods.resolve("range", null, "2026-01-01", BOGOTA, MONDAY_MORNING), "from", "required");
        assertViolation(
                () -> Periods.resolve("range", "ayer", "2026-01-01", BOGOTA, MONDAY_MORNING), "from", "invalid_format");
    }

    @Test
    void unknownPeriodIsRejected() {
        assertViolation(() -> Periods.resolve("year", null, null, BOGOTA, MONDAY_MORNING), "period", "invalid_value");
    }

    @Test
    void futureLocalDateIsDetected() {
        assertThat(Periods.isFutureDate(Instant.parse("2026-10-27T05:00:00Z"), BOGOTA, MONDAY_MORNING))
                .isTrue();
        assertThat(Periods.isFutureDate(Instant.parse("2026-10-27T04:59:59Z"), BOGOTA, MONDAY_MORNING))
                .isFalse();
    }

    private static void assertViolation(Runnable call, String field, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.violations()).contains(new FieldViolation(field, code)));
    }
}
