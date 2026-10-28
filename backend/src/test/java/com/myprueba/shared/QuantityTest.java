package com.myprueba.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuantityTest {

    @ParameterizedTest
    @CsvSource({"2,2000", "2.5,2500", "0.001,1", "999999999.999,999999999999"})
    void parsesValidQuantitiesToThousandths(String raw, long milli) {
        assertThat(Quantity.parseMilli("quantity", raw)).isEqualTo(milli);
    }

    @ParameterizedTest
    @CsvSource({"0,must_be_positive", "1.2345,invalid_format", "-1,invalid_format", "x,invalid_format"})
    void rejectsInvalidQuantities(String raw, String code) {
        assertThatThrownBy(() -> Quantity.parseMilli("items[0].quantity", raw))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.violations()).containsExactly(new FieldViolation("items[0].quantity", code)));
    }

    @Test
    void countsMayBeZeroButNotNegative() {
        assertThat(Quantity.parseNonNegativeMilli("quantity", "0")).isZero();
        assertThat(Quantity.parseNonNegativeMilli("quantity", "0.000")).isZero();
        assertThat(Quantity.parseNonNegativeMilli("quantity", "1.5")).isEqualTo(1500);
        assertThatThrownBy(() -> Quantity.parseNonNegativeMilli("quantity", "-1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void formatsNegativeQuantities() {
        assertThat(Quantity.format(-2000)).isEqualTo("-2");
    }

    @Test
    void formatsWithoutTrailingZeros() {
        assertThat(Quantity.format(2000)).isEqualTo("2");
        assertThat(Quantity.format(2500)).isEqualTo("2.5");
        assertThat(Quantity.format(1)).isEqualTo("0.001");
    }
}
