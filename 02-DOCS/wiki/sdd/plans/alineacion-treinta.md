---
type: plan
title: Plan — Alineación con Treinta (fase 1)
tags: [sdd, plan]
topic: sdd
slug: alineacion-treinta
status: approved in autopilot
---

# Plan — Alineación con Treinta

> Spec: [../specs/alineacion-treinta.md](../specs/alineacion-treinta.md)

## Contratos
- `PaymentMethod` = CASH, CARD, TRANSFER, NEQUI, DAVIPLATA, PAYMENT_LINK, OTHER (+ CREDIT solo
  interno para ventas fiadas).
- `SaleRequest` += `status` (PAID por defecto | CREDIT), `dueDate` (fecha local). Con CREDIT,
  `paymentMethod` no se envía. Respuesta += `status`, `dueDate`. `paymentMethod: CREDIT` en la
  entrada sigue aceptándose como sinónimo de `status: CREDIT` (compatibilidad).
- `ExpenseCategory` = UTILITIES, PURCHASES, RENT, PAYROLL, ADMINISTRATIVE, ADVERTISING, TRANSPORT,
  MAINTENANCE, EQUIPMENT, OTHER.
- `BusinessCategory` = 59 claves (de los `typeKey` observados, en MAYÚSCULAS_CON_GUION).
- Productos: `ProductRequest` += `stock` (solo al crear, ≥ 0, por defecto 0) y `minStock`.
  `GET /api/inventory` sin `tracked`; `PUT /api/inventory/{id}` solo cambia `minStock`.
- `GET/PUT /api/business/settings` → `{allowNegativeStock}`; venta que deja negativo con el ajuste
  apagado → 409 `insufficient_stock` con `productName`.
- Receivables: `customers[].oldestDebtAt`, `customers[].overdue`.

## Datos (V5)
- `sales.due_date DATE`; `businesses.allow_negative_stock BOOLEAN DEFAULT TRUE`.
- `UPDATE expenses SET category='OTHER' WHERE category='TAXES'`.
- Mapeo de categorías de negocio antiguas a las nuevas.
- `INSERT` de `stock_items` (tracked, 0) para productos que no tenían; `UPDATE stock_items SET tracked = TRUE`.

## Pruebas
Integración por criterio (`AlignmentApiTest`) y ajuste de las pruebas existentes al nuevo contrato;
componentes para venta pagada/fiada, producto con stock inicial, configuración y cuentas.
