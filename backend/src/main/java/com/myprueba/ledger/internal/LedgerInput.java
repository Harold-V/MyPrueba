package com.myprueba.ledger.internal;

import com.myprueba.shared.Money;
import com.myprueba.shared.Quantity;
import com.myprueba.shared.Text;
import com.myprueba.shared.ValidationException;
import com.myprueba.shared.Violations;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Parses and validates ledger requests, reporting every invalid field at once. */
final class LedgerInput {

    static final int MAX_CONCEPT = 140;
    static final int MAX_NOTE = 500;

    record ItemInput(UUID productId, String name, long quantityMilli, Long unitPriceCents) {}

    record SaleInput(
            Long amountCents,
            List<ItemInput> items,
            String concept,
            PaymentMethod paymentMethod,
            Instant occurredAt,
            String note) {}

    private LedgerInput() {}

    static SaleInput sale(SaleRequest request, ZoneId zone, Instant now) {
        Violations violations = new Violations();
        boolean hasAmount = request.amount() != null && !request.amount().isBlank();
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        Long amount = null;
        List<ItemInput> items = new ArrayList<>();
        if (hasAmount == hasItems) {
            violations.add("amount", "sale_kind");
        } else if (hasAmount) {
            amount = violations.capture(() -> Money.parseCents("amount", request.amount()));
        } else {
            for (int i = 0; i < request.items().size(); i++) {
                SaleItemRequest item = request.items().get(i);
                String prefix = "items[" + i + "].";
                boolean byProduct =
                        item.productId() != null && !item.productId().isBlank();
                boolean hasPrice = item.unitPrice() != null && !item.unitPrice().isBlank();
                UUID productId =
                        byProduct ? violations.capture(() -> uuid(prefix + "productId", item.productId())) : null;
                String name =
                        byProduct ? null : violations.capture(() -> Text.required(prefix + "name", item.name(), 80));
                Long quantity = violations.capture(() -> Quantity.parseMilli(prefix + "quantity", item.quantity()));
                // A product supplies its own price; a line without product must bring one.
                Long unitPrice = hasPrice || !byProduct
                        ? violations.capture(() -> Money.parseCents(prefix + "unitPrice", item.unitPrice()))
                        : null;
                if ((productId != null || name != null) && quantity != null) {
                    items.add(new ItemInput(productId, name, quantity, unitPrice));
                }
            }
        }
        String concept = violations.capture(() -> Text.optional("concept", request.concept(), MAX_CONCEPT));
        PaymentMethod method = violations.capture(
                () -> Text.requiredEnum("paymentMethod", request.paymentMethod(), PaymentMethod.class));
        Instant occurredAt = violations.capture(() -> occurredAt(request.occurredAt(), zone, now));
        String note = violations.capture(() -> Text.optional("note", request.note(), MAX_NOTE));
        violations.throwIfAny();
        return new SaleInput(amount, items, concept, method, occurredAt, note);
    }

    static ExpenseData expense(ExpenseRequest request, ZoneId zone, Instant now) {
        Violations violations = new Violations();
        Long amount = violations.capture(() -> Money.parseCents("amount", request.amount()));
        ExpenseCategory category =
                violations.capture(() -> Text.requiredEnum("category", request.category(), ExpenseCategory.class));
        String concept = violations.capture(() -> Text.optional("concept", request.concept(), MAX_CONCEPT));
        PaymentMethod method = violations.capture(
                () -> Text.requiredEnum("paymentMethod", request.paymentMethod(), PaymentMethod.class));
        Instant occurredAt = violations.capture(() -> occurredAt(request.occurredAt(), zone, now));
        String note = violations.capture(() -> Text.optional("note", request.note(), MAX_NOTE));
        violations.throwIfAny();
        return new ExpenseData(amount, category, concept, method, occurredAt, note);
    }

    private static Instant occurredAt(String raw, ZoneId zone, Instant now) {
        if (raw == null || raw.isBlank()) {
            return now;
        }
        Instant instant;
        try {
            instant = OffsetDateTime.parse(raw.strip()).toInstant();
        } catch (DateTimeParseException e) {
            throw ValidationException.of("occurredAt", "invalid_format");
        }
        if (Periods.isFutureDate(instant, zone, now)) {
            throw ValidationException.of("occurredAt", "future_date");
        }
        return instant;
    }

    private static UUID uuid(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw ValidationException.of(field, "required");
        }
        try {
            return UUID.fromString(raw.strip());
        } catch (IllegalArgumentException e) {
            throw ValidationException.of(field, "invalid_format");
        }
    }
}
