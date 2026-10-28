---
type: plan
title: Plan — Sistema de ventas
tags: [sdd, plan]
topic: sdd
slug: sistema-ventas
status: approved in autopilot
---

# Plan — Sistema de ventas

## Módulos
- **accounts**: el negocio gana `document`, `phone`, `address` (PUT /api/business). API pública
  `BusinessProfiles.profile(businessId)` para el comprobante.
- **ledger**: descuentos, número de venta, recibido/cambio, comprobante y caja
  (`CashRegisterService`, lee ventas, abonos, gastos y pagos a proveedores).
- **frontend**: página `/vender` (POS), `/ventas/:id/comprobante`, `/caja`; descuentos también en el
  formulario de venta; datos del negocio en Negocio.

## Contratos
```text
SaleRequest += items[i].discount | items[i].discountPercent, discount | discountPercent, received
SaleResponse += number, subtotal, discountTotal, received, change; items[i] += discount
  errores: items[i].discount "exceeds_line", discount "exceeds_subtotal", discount "items_only",
           received "less_than_total" | "cash_only"
GET /api/sales/{id}/receipt -> {business{name,document,phone,address}, number, occurredAt,
  customer{name,document,phone}|null, items, subtotal, discountTotal, total, paymentMethod,
  status, dueDate, received, change}
GET  /api/cash-register          -> {open: Session|null, last: Session|null}
POST /api/cash-register/open     {openingAmount}            409 cash_register_open
POST /api/cash-register/close    {countedAmount, note?}     409 cash_register_closed
GET  /api/cash-register/history  -> [Session] (cerradas, recientes primero, 30)
Session = {id, openedAt, opening, cashSales, cashPayments, cashExpenses, cashSupplierPayments,
           expected, closedAt?, counted?, difference?, note?}
```

## Datos (V9)
`businesses` + document, phone, address; `sales` + number, subtotal_cents, discount_cents,
received_cents; `sale_items` + discount_cents; índice único (business_id, number); numeración de
ventas existentes por created_at; `cash_sessions`.
Clientes: `CustomerView` += document (para el comprobante).
