package com.myprueba.shared;

import java.util.UUID;

public final class Ids {

    private Ids() {}

    /** An optional id in a request body or query: {@code null} when blank, a field error when malformed. */
    public static UUID optional(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.strip());
        } catch (IllegalArgumentException e) {
            throw ValidationException.of(field, "invalid_format");
        }
    }

    /** A malformed id cannot exist, so it is answered exactly like a missing one. */
    public static UUID parseOrNotFound(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }
}
