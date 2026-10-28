package com.myprueba.shared;

import java.util.UUID;

public final class Ids {

    private Ids() {}

    /** A malformed id cannot exist, so it is answered exactly like a missing one. */
    public static UUID parseOrNotFound(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }
}
