package com.myprueba.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Collects every field violation of a request so the client can show them all at once. */
public final class Violations {

    private final List<FieldViolation> violations = new ArrayList<>();

    public void add(String field, String code) {
        violations.add(new FieldViolation(field, code));
    }

    /** Runs a parser, recording its violations instead of throwing; returns {@code null} on failure. */
    public <T> T capture(Supplier<T> parser) {
        try {
            return parser.get();
        } catch (ValidationException e) {
            violations.addAll(e.violations());
            return null;
        }
    }

    public void throwIfAny() {
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
}
