package com.myprueba.ledger.internal;

import com.myprueba.shared.Money;
import com.myprueba.shared.Quantity;
import com.myprueba.shared.Text;
import com.myprueba.shared.ValidationException;
import com.myprueba.shared.Violations;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Parses and validates ledger requests, reporting every invalid field at once. */
final class LedgerInput {

    static final int MAX_CONCEPT = 140;
    static final int MAX_NOTE = 500;
    private static final DateTimeFormatter AUTOMATIC_CONCEPT = DateTimeFormatter.ofPattern("ddMMyyHHmmss");

    record ItemInput(UUID productId, String name, long quantityMilli, Long unitPriceCents) {}

    record SaleInput(
            Long amountCents,
            List<ItemInput> items,
            String concept,
            PaymentMethod paymentMethod,
            Instant occurredAt,
            String note,
            UUID customerId,
            LocalDate dueDate) {}

    record ExpenseItemInput(UUID productId, String name, long quantityMilli, Long unitCostCents) {}

    record ExpenseInput(
            Long amountCents,
            List<ExpenseItemInput> items,
            ExpenseCategory category,
            String concept,
            PaymentMethod paymentMethod,
            Instant occurredAt,
            String note,
            UUID supplierId,
            LocalDate dueDate) {}

    /** A payment to settle a debt; {@code partyId} is the customer or the supplier. */
    record PaymentInput(UUID partyId, long amountCents, PaymentMethod paymentMethod, Instant occurredAt, String note) {}

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
        PaymentStatus status =
                violations.capture(() -> Text.optionalEnum("status", request.status(), PaymentStatus.class));
        // "Fiado" is a status; paymentMethod CREDIT is still accepted as a synonym.
        boolean credit = status == PaymentStatus.CREDIT || "CREDIT".equals(request.paymentMethod());
        PaymentMethod method =
                credit ? PaymentMethod.CREDIT : violations.capture(() -> cashMethod(request.paymentMethod()));
        Instant occurredAt = violations.capture(() -> occurredAt(request.occurredAt(), zone, now));
        String note = violations.capture(() -> Text.optional("note", request.note(), MAX_NOTE));
        boolean hasCustomer =
                request.customerId() != null && !request.customerId().isBlank();
        UUID customerId = hasCustomer ? violations.capture(() -> uuid("customerId", request.customerId())) : null;
        if (credit && !hasCustomer) {
            violations.add("customerId", "required");
        }
        LocalDate dueDate = credit ? violations.capture(() -> date("dueDate", request.dueDate())) : null;
        if (dueDate != null && occurredAt != null && dueDate.isBefore(LocalDate.ofInstant(occurredAt, zone))) {
            violations.add("dueDate", "before_sale");
        }
        violations.throwIfAny();
        return new SaleInput(amount, items, concept, method, occurredAt, note, customerId, dueDate);
    }

    static PaymentInput payment(PaymentRequest request, ZoneId zone, Instant now) {
        return payment(
                "customerId",
                request.customerId(),
                request.amount(),
                request.paymentMethod(),
                request.occurredAt(),
                request.note(),
                zone,
                now);
    }

    static PaymentInput payment(SupplierPaymentRequest request, ZoneId zone, Instant now) {
        return payment(
                "supplierId",
                request.supplierId(),
                request.amount(),
                request.paymentMethod(),
                request.occurredAt(),
                request.note(),
                zone,
                now);
    }

    private static PaymentInput payment(
            String partyField,
            String rawParty,
            String rawAmount,
            String rawMethod,
            String rawOccurredAt,
            String rawNote,
            ZoneId zone,
            Instant now) {
        Violations violations = new Violations();
        UUID partyId = violations.capture(() -> uuid(partyField, rawParty));
        Long amount = violations.capture(() -> Money.parseCents("amount", rawAmount));
        PaymentMethod method = violations.capture(() -> cashMethod(rawMethod));
        Instant occurredAt = violations.capture(() -> occurredAt(rawOccurredAt, zone, now));
        String note = violations.capture(() -> Text.optional("note", rawNote, MAX_NOTE));
        violations.throwIfAny();
        return new PaymentInput(partyId, amount, method, occurredAt, note);
    }

    /** A payment method that moves money now: anything but {@link PaymentMethod#CREDIT}. */
    private static PaymentMethod cashMethod(String raw) {
        PaymentMethod method = Text.requiredEnum("paymentMethod", raw, PaymentMethod.class);
        if (method == PaymentMethod.CREDIT) {
            throw ValidationException.of("paymentMethod", "invalid_value");
        }
        return method;
    }

    /**
     * An expense has an amount, or product lines (a purchase: its category is PURCHASES and its
     * amount is the sum of the lines). On credit it is owed to a supplier.
     */
    static ExpenseInput expense(ExpenseRequest request, ZoneId zone, Instant now) {
        Violations violations = new Violations();
        boolean hasAmount = request.amount() != null && !request.amount().isBlank();
        boolean hasItems = request.items() != null && !request.items().isEmpty();
        Long amount = null;
        List<ExpenseItemInput> items = new ArrayList<>();
        if (hasAmount == hasItems) {
            violations.add("amount", "expense_kind");
        } else if (hasAmount) {
            amount = violations.capture(() -> Money.parseCents("amount", request.amount()));
        } else {
            for (int i = 0; i < request.items().size(); i++) {
                ExpenseItemRequest item = request.items().get(i);
                String prefix = "items[" + i + "].";
                boolean byProduct =
                        item.productId() != null && !item.productId().isBlank();
                boolean hasCost = item.unitCost() != null && !item.unitCost().isBlank();
                UUID productId =
                        byProduct ? violations.capture(() -> uuid(prefix + "productId", item.productId())) : null;
                String name =
                        byProduct ? null : violations.capture(() -> Text.required(prefix + "name", item.name(), 80));
                Long quantity = violations.capture(() -> Quantity.parseMilli(prefix + "quantity", item.quantity()));
                // A product may supply its own cost; a line without product must bring one.
                Long unitCost = hasCost || !byProduct
                        ? violations.capture(() -> Money.parseCents(prefix + "unitCost", item.unitCost()))
                        : null;
                if ((productId != null || name != null) && quantity != null) {
                    items.add(new ExpenseItemInput(productId, name, quantity, unitCost));
                }
            }
        }
        boolean hasCategory = request.category() != null && !request.category().isBlank();
        ExpenseCategory category = hasItems && !hasCategory
                ? ExpenseCategory.PURCHASES
                : violations.capture(() -> Text.requiredEnum("category", request.category(), ExpenseCategory.class));
        if (hasItems && category != null && category != ExpenseCategory.PURCHASES) {
            violations.add("category", "purchase_category");
        }
        String concept = violations.capture(() -> Text.optional("concept", request.concept(), MAX_CONCEPT));
        PaymentStatus status =
                violations.capture(() -> Text.optionalEnum("status", request.status(), PaymentStatus.class));
        boolean credit = status == PaymentStatus.CREDIT || "CREDIT".equals(request.paymentMethod());
        PaymentMethod method =
                credit ? PaymentMethod.CREDIT : violations.capture(() -> cashMethod(request.paymentMethod()));
        Instant occurredAt = violations.capture(() -> occurredAt(request.occurredAt(), zone, now));
        String note = violations.capture(() -> Text.optional("note", request.note(), MAX_NOTE));
        boolean hasSupplier =
                request.supplierId() != null && !request.supplierId().isBlank();
        UUID supplierId = hasSupplier ? violations.capture(() -> uuid("supplierId", request.supplierId())) : null;
        if (credit && !hasSupplier) {
            violations.add("supplierId", "required");
        }
        LocalDate dueDate = credit ? violations.capture(() -> date("dueDate", request.dueDate())) : null;
        if (dueDate != null && occurredAt != null && dueDate.isBefore(LocalDate.ofInstant(occurredAt, zone))) {
            violations.add("dueDate", "before_expense");
        }
        violations.throwIfAny();
        // A purchase is described by its lines; any other expense without concept by its moment.
        String description = concept != null || hasItems ? concept : automaticExpenseConcept(occurredAt, zone);
        return new ExpenseInput(amount, items, category, description, method, occurredAt, note, supplierId, dueDate);
    }

    /** An expense without concept is named after its moment, e.g. "Gasto 281026031423" (ddMMyyHHmmss). */
    static String automaticExpenseConcept(Instant occurredAt, ZoneId zone) {
        return "Gasto " + AUTOMATIC_CONCEPT.format(occurredAt.atZone(zone));
    }

    private static LocalDate date(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw ValidationException.of(field, "invalid_format");
        }
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
