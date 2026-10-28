package com.myprueba.ledger.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Validated, storage-shaped values (money in cents, quantities in thousandths). */
record SaleLine(UUID productId, String productName, long unitPriceCents, long quantityMilli, long lineTotalCents) {}

record SaleData(
        SaleKind kind,
        long totalCents,
        List<SaleLine> lines,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record ExpenseData(
        long amountCents,
        ExpenseCategory category,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record MovementRow(
        UUID id,
        MovementType type,
        long amountCents,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        ExpenseCategory category) {}
