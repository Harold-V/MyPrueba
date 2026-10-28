package com.myprueba.ledger.internal;

enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    OTHER,
    /** Fiado: the customer pays later. Only valid on sales, and requires a customer. */
    CREDIT
}

enum ExpenseCategory {
    PURCHASES,
    RENT,
    UTILITIES,
    PAYROLL,
    TRANSPORT,
    MAINTENANCE,
    TAXES,
    OTHER
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
