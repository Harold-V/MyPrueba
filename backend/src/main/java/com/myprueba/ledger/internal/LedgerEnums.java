package com.myprueba.ledger.internal;

enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    OTHER
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
    EXPENSE
}
