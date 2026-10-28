package com.myprueba.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({"15000,1500000", "15000.5,1500050", "0.10,10", "999999999999.99,99999999999999"})
    void parsesValidAmountsToCents(String raw, long cents) {
        assertThat(Money.parseCents("amount", raw)).isEqualTo(cents);
    }

    @ParameterizedTest
    @CsvSource({
        "0,must_be_positive",
        "0.00,must_be_positive",
        "-5,invalid_format",
        "1.234,invalid_format",
        "abc,invalid_format",
        "1e3,invalid_format",
        "1000000000000,invalid_format"
    })
    void rejectsInvalidAmounts(String raw, String code) {
        assertThatThrownBy(() -> Money.parseCents("amount", raw))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.violations()).containsExactly(new FieldViolation("amount", code)));
    }

    @Test
    void rejectsMissingAmount() {
        assertThatThrownBy(() -> Money.parseCents("amount", null))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.violations()).containsExactly(new FieldViolation("amount", "required")));
    }

    @Test
    void formatsCentsWithTwoDecimals() {
        assertThat(Money.format(1500000)).isEqualTo("15000.00");
        assertThat(Money.format(30)).isEqualTo("0.30");
        assertThat(Money.format(-3000000)).isEqualTo("-30000.00");
    }

    @Test
    void sumsOfCentsAreExact() {
        long total = Money.parseCents("a", "0.10") * 3;
        assertThat(Money.format(total)).isEqualTo("0.30");
    }

    @Test
    void lineTotalRoundsHalfUpToCents() {
        // 2.5 x 3000.00 = 7500.00
        assertThat(Money.lineTotalCents(2500, 300000)).isEqualTo(750000);
        // 0.333 x 0.05 = 0.01665 -> 0.02
        assertThat(Money.lineTotalCents(333, 5)).isEqualTo(2);
        // 0.001 x 0.01 = 0.00001 -> 0.00
        assertThat(Money.lineTotalCents(1, 1)).isZero();
    }

    @Test
    void lineTotalAboveMaximumIsRejected() {
        assertThatThrownBy(() -> Money.lineTotalCents(999_999_999_999L, Money.MAX_CENTS))
                .isInstanceOf(ValidationException.class);
    }
}
