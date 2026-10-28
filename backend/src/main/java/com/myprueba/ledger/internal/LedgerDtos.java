package com.myprueba.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A line either references a product ({@code productId}) or carries its own {@code name} and
 * {@code unitPrice} — the latter keeps lines of deleted products editable.
 */
record SaleItemRequest(String productId, String name, String quantity, String unitPrice) {}

record SaleRequest(
        String amount,
        List<SaleItemRequest> items,
        String concept,
        String paymentMethod,
        String occurredAt,
        String note) {}

record SaleItemResponse(UUID productId, String name, String unitPrice, String quantity, String lineTotal) {}

record SaleResponse(
        UUID id,
        MovementType type,
        SaleKind kind,
        String total,
        List<SaleItemResponse> items,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record ExpenseRequest(
        String amount, String category, String concept, String paymentMethod, String occurredAt, String note) {}

record ExpenseResponse(
        UUID id,
        MovementType type,
        String amount,
        ExpenseCategory category,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record PeriodView(LocalDate from, LocalDate to) {

    static PeriodView of(Period period) {
        return new PeriodView(period.from(), period.to());
    }
}

record MovementResponse(
        UUID id,
        MovementType type,
        String amount,
        String description,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        ExpenseCategory category) {}

record MovementsResponse(PeriodView period, List<MovementResponse> items) {}

record BalanceResponse(PeriodView period, String sales, String expenses, String balance) {}
