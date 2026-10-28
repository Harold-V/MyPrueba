package com.myprueba.shared;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/** Money travels as a decimal string, lives as {@code long} cents in storage (principle 23). */
public final class Money {

    public static final long MAX_CENTS = 99_999_999_999_999L;

    private static final Pattern FORMAT = Pattern.compile("\\d{1,12}(\\.\\d{1,2})?");
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    private Money() {}

    public static long parseCents(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw ValidationException.of(field, "required");
        }
        String value = raw.strip();
        if (!FORMAT.matcher(value).matches()) {
            throw ValidationException.of(field, "invalid_format");
        }
        long cents = new BigDecimal(value).movePointRight(2).longValueExact();
        if (cents <= 0) {
            throw ValidationException.of(field, "must_be_positive");
        }
        return cents;
    }

    public static String format(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    /** {@code quantity × unit price}, rounded half-up to cents. */
    public static long lineTotalCents(long quantityMilli, long unitPriceCents) {
        BigInteger[] division = BigInteger.valueOf(quantityMilli)
                .multiply(BigInteger.valueOf(unitPriceCents))
                .divideAndRemainder(THOUSAND);
        BigInteger cents = division[0];
        if (division[1].compareTo(BigInteger.valueOf(500)) >= 0) {
            cents = cents.add(BigInteger.ONE);
        }
        return requireWithinMax(cents);
    }

    public static long add(long left, long right) {
        return requireWithinMax(BigInteger.valueOf(left).add(BigInteger.valueOf(right)));
    }

    private static long requireWithinMax(BigInteger cents) {
        if (cents.compareTo(BigInteger.valueOf(MAX_CENTS)) > 0) {
            throw ValidationException.of("amount", "amount_too_large");
        }
        return cents.longValueExact();
    }
}
