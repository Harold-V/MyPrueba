package com.myprueba.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;
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
        String note,
        UUID customerId,
        String customerName,
        LocalDate dueDate) {}

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
