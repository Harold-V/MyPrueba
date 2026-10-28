---
type: plan
title: Plan — Libro de ventas, gastos y balance
description: Plan de estructura para productos, ventas, gastos, movimientos y balance.
tags: [sdd, plan]
timestamp: 2026-10-28T06:00:00Z
topic: sdd
slug: libro-ventas-gastos-balance
status: approved in autopilot
---

# Plan — Libro de ventas, gastos y balance

> Spec: [../specs/libro-ventas-gastos-balance.md](../specs/libro-ventas-gastos-balance.md) · Constitution: [../constitution.md](../constitution.md) v1.1.0 · Status: approved in autopilot
> Last updated: 2026-10-28. Hereda §0 Global Constraints y la arquitectura de
> [cuentas-y-negocio](cuentas-y-negocio.md).

## 0. Global Constraints

Las de [cuentas-y-negocio §0](cuentas-y-negocio.md#0-global-constraints), más:

- **Valor de movimiento:** string `^\d{1,12}(\.\d{1,2})?$`, > 0, ≤ `999999999999.99`.
- **Cantidad:** string `^\d{1,9}(\.\d{1,3})?$`, > 0.
- **Total de línea:** `round_half_up(cantidad × precio unitario)` a centavos; total de venta = suma de
  líneas; si supera el máximo de valor → 400 `amount_too_large`.
- **Fechas:** `occurredAt` es un instante ISO 8601; se rechaza (400 `future_date`) si su fecha local
  en la zona del negocio es posterior a hoy. Sin `occurredAt` → ahora.
- **Periodos** (zona del negocio, límites `[inicio, fin)`): `today`; `week` = lunes 00:00 a lunes
  siguiente; `month` = día 1 al día 1 siguiente; `range` = `from`..`to` fechas locales inclusivas,
  `from ≤ to`, máximo 366 días.

## 1. Context & constraints

- Criterios que dan forma: totales exactos (0,10 × 3 = 0,30), venta conserva nombre y precio,
  semana lunes-domingo en hora de Colombia, aislamiento → 404.
- Fuera de alcance: stock, crédito, clientes, costos, reportes, comprobantes.

## 2. Architecture

- **catalog** (internal) — productos; expone `ProductCatalog.find(businessId, productId) -> Optional<ProductView(id, name, price)>`.
- **ledger** (internal) — ventas, gastos, movimientos y balance; usa `CurrentBusiness` (accounts) y
  `ProductCatalog` (catalog) solo por sus API públicas.

**Decisión principal:** la venta con productos guarda una **copia** (nombre, precio unitario,
cantidad, total de línea) en cada línea, y el enlace al producto se pone a nulo si este se borra.
Así el historial nunca cambia aunque cambie el catálogo, que es lo que exige la spec.

## 3. Interfaces & contracts

```text
GET    /api/products -> 200 [Product]                 (orden por nombre)
POST   /api/products {name, price} -> 201 Product | 400 | 409 code=product_name_taken
PUT    /api/products/{id} {name, price} -> 200 Product | 400 | 404 | 409
DELETE /api/products/{id} -> 204 | 404
Product = {id, name, price:"3000.00"}

POST   /api/sales SaleInput -> 201 Sale | 400 | 404 code=product_not_found
GET    /api/sales/{id} -> 200 Sale | 404
PUT    /api/sales/{id} SaleInput -> 200 Sale | 400 | 404
DELETE /api/sales/{id} -> 204 | 404
SaleInput = {amount?, items?:[{productId, quantity, unitPrice?}], concept?, paymentMethod, occurredAt?, note?}
  - exactamente uno de `amount` (venta libre) o `items` (≥ 1 línea) → si no, 400 code=sale_kind
Sale = {id, type:"SALE", kind:"FREE"|"ITEMS", total, items:[{productId|null, name, unitPrice, quantity, lineTotal}],
        concept|null, paymentMethod, occurredAt, note|null}
paymentMethod ∈ {CASH, CARD, TRANSFER, OTHER}

POST/GET/PUT/DELETE /api/expenses[/{id}] ExpenseInput {amount, category, concept?, paymentMethod, occurredAt?, note?}
Expense = {id, type:"EXPENSE", amount, category, concept|null, paymentMethod, occurredAt, note|null}
category ∈ {PURCHASES, RENT, UTILITIES, PAYROLL, TRANSPORT, MAINTENANCE, TAXES, OTHER}

GET /api/movements?period=today|week|month|range&from=&to=&type=all|sales|expenses
  -> 200 {period:{from,to}, items:[Movement]}  (occurredAt desc, luego createdAt desc)
Movement = {id, type:"SALE"|"EXPENSE", amount, description, paymentMethod, occurredAt, category|null}
GET /api/balance?period=…&from=&to= -> 200 {period:{from,to}, sales, expenses, balance}
```

`concept` y `note` ≤ 140 y ≤ 500 caracteres; nombre de producto 1..80 tras quitar espacios.

## 4. Data model & flow

- **products** — id, business_id → businesses, name, name_key (minúsculas, UNIQUE con business_id), price_cents, timestamps.
- **sales** — id, business_id, kind, total_cents, concept, payment_method, occurred_at, note, timestamps.
- **sale_items** — id, sale_id → sales (ON DELETE CASCADE), product_id → products (ON DELETE SET NULL), product_name, unit_price_cents, quantity_milli, line_total_cents, position.
- **expenses** — id, business_id, amount_cents, category, concept, payment_method, occurred_at, note, timestamps.
- Índices: `(business_id, occurred_at)` en sales y expenses.

Flujo venta con productos: validar → para cada línea `ProductCatalog.find` (ajeno/inexistente → 404) →
precio = `unitPrice` o el del producto → calcular líneas y total → transacción {sale + items}.
Balance: `SUM(total_cents)` / `SUM(amount_cents)` en SQL dentro de `[from, to)` → enteros exactos.

## 5. Testing strategy

| Criterio | Nivel | Afirma |
| --- | --- | --- |
| estado vacío, venta libre, venta con productos, gasto, balance negativo | integración | totales y listas |
| copia de nombre/precio tras editar/borrar producto | integración | venta intacta |
| 0,10 × 3 = 0,30; valores inválidos; fecha futura | unit (Money) + integración | rechazo con `field` |
| domingo 23:30 Bogotá fuera de "esta semana" | unit (Periods con `Clock` fijo) | límites |
| editar/eliminar, filtro gastos | integración | listas y balance |
| aislamiento A/B en productos, ventas y gastos | integración | 404 y sin cambios |
| p95 ≤ 300 ms | integración | 200 peticiones de balance con 2.000 movimientos |
| pantallas de inicio, venta, gasto, productos | componente | flujo y errores |

## 6. Sequencing & dependencies

Periods y migración V2 primero; catalog antes que ventas con productos; gastos en paralelo con
ventas; movimientos y balance al final del backend; frontend tras el contrato §3. Detalle en Tasks.

## Tasks
<!-- generated by tasks on 2026-10-28; IDs are stable, do not renumber -->

| ID | [P] | Task | Done-check | Depends-on | Trace |
| --- | --- | --- | --- | --- | --- |
| T101 |  | Periods (today/week/month/range) con `Clock` | tests unit verdes (incl. domingo 23:30) | T003 | spec AC 10 |
| T102 |  | Migración V2 products/sales/sale_items/expenses | Flyway aplica | T004 | plan §4 |
| T103 |  | catalog: CRUD productos + nombre único + A/B | tests integración verdes | T102, T006 | spec Main path productos |
| T104 |  | ledger: ventas libres y con productos, copia, editar/eliminar | tests integración verdes | T103, T101 | spec AC 2-4, 11 |
| T105 |  | ledger: gastos CRUD | tests integración verdes | T102, T101 | spec AC 5, 8, 9 |
| T106 |  | ledger: movimientos + balance + filtro + A/B + p95 | tests integración verdes | T104, T105 | spec AC 1, 6, 7, 12, 13 |
| T107 |  | Frontend: inicio (periodo, balance, movimientos, vacío) | tests componente verdes | T009, T106 | spec AC 1, 6, 12 |
| T108 |  | Frontend: nueva/editar venta (libre y con productos) y gasto | tests componente verdes | T107 | spec AC 2-5, 8, 11 |
| T109 |  | Frontend: productos (lista, crear, editar, eliminar) | tests componente verdes | T009 | spec Main path productos |
| T110 |  | Todos los done-checks + `scripts/verify.sh` verde | sale 0 | all | spec §Acceptance |

**Forecast:** ~3.5k líneas con tests. Mismo PR que cuentas-y-negocio (primera entrega).
