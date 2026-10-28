---
type: plan
title: Plan — Proveedores, compras y cuentas por pagar
tags: [sdd, plan]
topic: sdd
slug: proveedores-compras
status: approved in autopilot
---

# Plan — Proveedores, compras y cuentas por pagar

## Módulos
- **suppliers** (nuevo): directorio, espejo de `customers`: `SupplierDirectory`, `SupplierDeleting` (veto).
- **ledger**: gastos con proveedor, estado, vencimiento y líneas; abonos a proveedores; cuentas por
  pagar; publica `PurchaseItemsChanged(before, after)`; veta `SupplierDeleting` con deuda.
- **inventory**: escucha `PurchaseItemsChanged` (suma stock; tipo de movimiento `PURCHASE`, `PURCHASE_EDIT`, `PURCHASE_DELETE`).

## Contratos
```text
/api/suppliers CRUD  Supplier = {id, name, phone, document, note}
ExpenseRequest += supplierId?, status? (PAID|CREDIT), dueDate?, items?[{productId, quantity, unitCost?}]
  items → valor = Σ líneas (amount no se envía) y category = PURCHASES
Expense += supplierId, supplierName, status, dueDate, items[{productId|null, name, unitCost, quantity, lineTotal}]
POST/GET/PUT/DELETE /api/supplier-payments   {supplierId, amount, paymentMethod, occurredAt?, note?}
GET /api/payables -> {total, suppliers:[{supplierId, name, phone, debt, oldestDebtAt, overdue}]}
GET /api/suppliers/{id}/account   -> {supplier, debt, entries[EXPENSE|PAYMENT]}
GET /api/suppliers/{id}/purchases -> {total, count, items[{id, status, total, description, occurredAt}]}
Balance: expenses = gastos pagados + abonos a proveedores; += payables (total por pagar)
Movement type += SUPPLIER_PAYMENT; gastos a crédito aparecen con paymentMethod CREDIT
```

## Datos (V7)
`suppliers`; `expenses` + supplier_id, supplier_name, due_date; `expense_items`; `supplier_payments`.
