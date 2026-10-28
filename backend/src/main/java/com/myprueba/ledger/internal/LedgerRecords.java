package com.myprueba.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Validated, storage-shaped values (money in cents, quantities in thousandths). */
/** {@code lineTotalCents} is quantity × unit price; {@code discountCents} comes off it. */
record SaleLine(
        UUID productId,
        String productName,
        long unitPriceCents,
        long quantityMilli,
        long lineTotalCents,
        long discountCents) {}

/**
 * A sale: {@code subtotalCents} is the sum of its lines, {@code discountCents} the discount on the
 * whole sale (line discounts live on the lines) and {@code totalCents} what was charged.
 */
record SaleData(
        Long number,
        SaleKind kind,
        long subtotalCents,
        long discountCents,
        long totalCents,
        List<SaleLine> lines,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note,
        UUID customerId,
        String customerName,
        LocalDate dueDate,
        Long receivedCents) {

    long discountTotalCents() {
        return subtotalCents - totalCents;
    }

    SaleData withLines(List<SaleLine> lines) {
        return new SaleData(
                number,
                kind,
                subtotalCents,
                discountCents,
                totalCents,
                lines,
                concept,
                paymentMethod,
                occurredAt,
                note,
                customerId,
                customerName,
                dueDate,
                receivedCents);
    }
}

record ExpenseLine(UUID productId, String productName, long unitCostCents, long quantityMilli, long lineTotalCents) {}

record ExpenseData(
        long amountCents,
        List<ExpenseLine> lines,
        ExpenseCategory category,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note,
        UUID supplierId,
        String supplierName,
        LocalDate dueDate) {

    ExpenseData withLines(List<ExpenseLine> lines) {
        return new ExpenseData(
                amountCents,
                lines,
                category,
                concept,
                paymentMethod,
                occurredAt,
                note,
                supplierId,
                supplierName,
                dueDate);
    }
}

record MovementRow(
        UUID id,
        MovementType type,
        long amountCents,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        ExpenseCategory category,
        String customerName,
        String supplierName) {}

record PaymentData(
        UUID customerId,
        String customerName,
        long amountCents,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record SupplierPaymentData(
        UUID supplierId,
        String supplierName,
        long amountCents,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}
