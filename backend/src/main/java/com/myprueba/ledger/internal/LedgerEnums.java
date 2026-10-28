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
    /** Paid later: a credit sale requires a customer, a credit expense requires a supplier. */
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

/**
 * Paid now, or owed: a credit sale is owed by a customer (fiado), a credit expense is owed to a
 * supplier. Stored as payment method CREDIT.
 */
enum PaymentStatus {
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
    PAYMENT,
    SUPPLIER_PAYMENT
}
