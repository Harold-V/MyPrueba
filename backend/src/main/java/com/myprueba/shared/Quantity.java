package com.myprueba.shared;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Quantities travel as decimal strings and are stored as {@code long} thousandths. */
public final class Quantity {

    private static final Pattern FORMAT = Pattern.compile("\\d{1,9}(\\.\\d{1,3})?");

    private Quantity() {}

    public static long parseMilli(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw ValidationException.of(field, "required");
        }
        String value = raw.strip();
        if (!FORMAT.matcher(value).matches()) {
            throw ValidationException.of(field, "invalid_format");
        }
        long milli = new BigDecimal(value).movePointRight(3).longValueExact();
        if (milli <= 0) {
            throw ValidationException.of(field, "must_be_positive");
        }
        return milli;
    }

    /** Like {@link #parseMilli} but accepts zero, for counts. */
    public static long parseNonNegativeMilli(String field, String raw) {
        if (raw != null && FORMAT.matcher(raw.strip()).matches() && new BigDecimal(raw.strip()).signum() == 0) {
            return 0;
        }
        return parseMilli(field, raw);
    }

    public static String format(long milli) {
        return BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString();
    }
}
