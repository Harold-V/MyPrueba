package com.myprueba.ledger.internal;

/** Payment methods offered in Colombia (verified with REA). */
enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    NEQUI,
    DAVIPLATA,
    PAYMENT_LINK,
    OTHER,
    /** Fiado: the customer pays later. Only valid on sales, and requires a customer. */
    CREDIT
}

/** Expense categories (verified with REA). */
enum ExpenseCategory {
    UTILITIES,
    PURCHASES,
    RENT,
    PAYROLL,
    ADMINISTRATIVE,
    ADVERTISING,
    TRANSPORT,
    MAINTENANCE,
    EQUIPMENT,
    OTHER
}

/** A sale is paid now or owed by a customer (fiado). Stored as payment method CREDIT. */
enum SaleStatus {
    PAID,
    CREDIT
}

enum SaleKind {
    FREE,
    ITEMS
}

enum MovementType {
    SALE,
    EXPENSE,
    PAYMENT
}
