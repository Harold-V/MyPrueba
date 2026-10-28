package com.myprueba.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A line either references a product ({@code productId}) or carries its own {@code name} and
 * {@code unitPrice} — the latter keeps lines of deleted products editable.
 */
record SaleItemRequest(
        String productId, String name, String quantity, String unitPrice, String discount, String discountPercent) {}

record SaleRequest(
        String amount,
        List<SaleItemRequest> items,
        String concept,
        String paymentMethod,
        String occurredAt,
        String note,
        String customerId,
        String status,
        String dueDate,
        String discount,
        String discountPercent,
        String received) {}

/** {@code lineTotal} is quantity × unit price, before the line's {@code discount}. */
record SaleItemResponse(
        UUID productId, String name, String unitPrice, String quantity, String lineTotal, String discount) {}

/**
 * {@code subtotal} is the sum of the lines before discounts; {@code discountTotal} adds line and
 * sale discounts; {@code total} is what was charged.
 */
record SaleResponse(
        UUID id,
        long number,
        MovementType type,
        SaleKind kind,
        String subtotal,
        String discountTotal,
        String total,
        List<SaleItemResponse> items,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note,
        UUID customerId,
        String customerName,
        PaymentStatus status,
        LocalDate dueDate,
        String received,
        String change) {}

record ReceiptBusiness(String name, String document, String phone, String address) {}

record ReceiptCustomer(String name, String document, String phone) {}

/** Everything a digital receipt shows, with the business and customer data as they are now. */
record ReceiptResponse(
        UUID id,
        long number,
        Instant occurredAt,
        ReceiptBusiness business,
        ReceiptCustomer customer,
        List<SaleItemResponse> items,
        String subtotal,
        String discountTotal,
        String total,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        LocalDate dueDate,
        String received,
        String change,
        String note) {}

/** A purchase line: a product with an optional {@code unitCost} (defaults to the product's cost). */
record ExpenseItemRequest(String productId, String name, String quantity, String unitCost) {}

record ExpenseRequest(
        String amount,
        List<ExpenseItemRequest> items,
        String category,
        String concept,
        String paymentMethod,
        String occurredAt,
        String note,
        String supplierId,
        String status,
        String dueDate) {}

record ExpenseItemResponse(UUID productId, String name, String unitCost, String quantity, String lineTotal) {}

record ExpenseResponse(
        UUID id,
        MovementType type,
        String amount,
        List<ExpenseItemResponse> items,
        ExpenseCategory category,
        String concept,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note,
        UUID supplierId,
        String supplierName,
        PaymentStatus status,
        LocalDate dueDate) {}

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
        ExpenseCategory category,
        String customerName,
        String supplierName) {}

record MovementsResponse(PeriodView period, List<MovementResponse> items) {}

/**
 * Cash basis: {@code balance = sales (paid) + payments − expenses}, where expenses are paid expenses
 * plus payments to suppliers. {@code receivable} and {@code payables} are all-time.
 */
record BalanceResponse(
        PeriodView period,
        String sales,
        String payments,
        String expenses,
        String balance,
        String receivable,
        String payables) {}

record PaymentRequest(String customerId, String amount, String paymentMethod, String occurredAt, String note) {}

record PaymentResponse(
        UUID id,
        MovementType type,
        UUID customerId,
        String customerName,
        String amount,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record CustomerDebt(UUID customerId, String name, String phone, String debt, Instant oldestDebtAt, boolean overdue) {}

record ReceivablesResponse(String total, List<CustomerDebt> customers) {}

record AccountCustomer(UUID id, String name, String phone) {}

record AccountEntry(UUID id, MovementType type, String amount, String description, Instant occurredAt) {}

record AccountResponse(AccountCustomer customer, String debt, List<AccountEntry> entries) {}

record PurchaseEntry(UUID id, PaymentStatus status, String total, String description, Instant occurredAt) {}

/** Every sale of one customer (or expense with one supplier), paid or on credit, newest first. */
record PurchasesResponse(String total, int count, List<PurchaseEntry> items) {}

record SupplierPaymentRequest(String supplierId, String amount, String paymentMethod, String occurredAt, String note) {}

record SupplierPaymentResponse(
        UUID id,
        MovementType type,
        UUID supplierId,
        String supplierName,
        String amount,
        PaymentMethod paymentMethod,
        Instant occurredAt,
        String note) {}

record SupplierDebt(UUID supplierId, String name, String phone, String debt, Instant oldestDebtAt, boolean overdue) {}

record PayablesResponse(String total, List<SupplierDebt> suppliers) {}

record AccountSupplier(UUID id, String name, String phone) {}

record SupplierAccountResponse(AccountSupplier supplier, String debt, List<AccountEntry> entries) {}
