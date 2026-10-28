package com.myprueba.shared;

/** Helpers for optional and required free-text fields. */
public final class Text {

    private Text() {}

    /** Trimmed value, or {@code null} when absent or blank; rejects values longer than {@code max}. */
    public static String optional(String field, String raw, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > max) {
            throw ValidationException.of(field, "too_long");
        }
        return value;
    }

    public static String required(String field, String raw, int max) {
        String value = optional(field, raw, max);
        if (value == null) {
            throw ValidationException.of(field, "required");
        }
        return value;
    }

    /** Parses an enum constant by exact name; {@code null} when absent. */
    public static <E extends Enum<E>> E optionalEnum(String field, String raw, Class<E> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip());
        } catch (IllegalArgumentException e) {
            throw ValidationException.of(field, "invalid_value");
        }
    }

    public static <E extends Enum<E>> E requiredEnum(String field, String raw, Class<E> type) {
        E value = optionalEnum(field, raw, type);
        if (value == null) {
            throw ValidationException.of(field, "required");
        }
        return value;
    }
}
