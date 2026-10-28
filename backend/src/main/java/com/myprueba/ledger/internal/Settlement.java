package com.myprueba.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Payments settle the oldest credit entries first. The first entry with a balance left tells "since
 * when" money is owed; any unsettled entry past its due date makes the debt overdue.
 */
final class Settlement {

    /** A credit sale or a credit expense. */
    record Owed(long totalCents, Instant occurredAt, LocalDate dueDate) {}

    record Unpaid(Instant oldest, boolean overdue) {}

    private Settlement() {}

    /** {@code owed} must be oldest first; {@code debt} is what is still owed in total. */
    static Unpaid unpaid(List<Owed> owed, long debt, LocalDate today) {
        if (debt <= 0) {
            return new Unpaid(null, false);
        }
        long paid = owed.stream().mapToLong(Owed::totalCents).sum() - debt;
        Instant oldest = null;
        boolean overdue = false;
        for (Owed entry : owed) {
            if (paid >= entry.totalCents()) {
                paid -= entry.totalCents();
                continue;
            }
            paid = 0;
            oldest = oldest == null ? entry.occurredAt() : oldest;
            overdue = overdue || (entry.dueDate() != null && entry.dueDate().isBefore(today));
        }
        return new Unpaid(oldest, overdue);
    }
}
