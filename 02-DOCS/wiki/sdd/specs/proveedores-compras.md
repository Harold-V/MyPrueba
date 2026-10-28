---
type: spec
title: Spec — Proveedores, compras y cuentas por pagar (fase 3)
description: Proveedores, compras de mercancía que suben el stock, compras a crédito y abonos a proveedores.
tags: [sdd, spec]
timestamp: 2026-10-28T20:00:00Z
topic: sdd
slug: proveedores-compras
status: approved in autopilot
---

# Spec — Proveedores, compras y cuentas por pagar (fase 3)

> Slug: `proveedores-compras` · Status: approved in autopilot (2026-10-28) · Inherits: constitution v1.1.0
> Fuentes: [página de proveedores](https://www.treinta.co/programa-de-gestion-de-proveedores-para-tu-negocio/),
> [inventarios](https://www.treinta.co/software-de-control-de-inventarios-para-tu-negocio/),
> [deudas](https://www.treinta.co/control-de-deudas/) (✔ = descrito ahí) y
> [HAR](../../research/treinta-har-2026-10-28.md) (★ = observado: gastos a crédito, proveedores como
> contactos, abonos a deudas que se ven en egresos).

## Problem & why
El dueño compra mercancía a proveedores, a veces a crédito. Hoy no puede registrar a quién le
compra, la compra no sube el stock y no sabe cuánto debe ni a quién.

## Cost of not building it
Doble trabajo (gasto + ajuste de stock), deudas con proveedores olvidadas y un balance de caja que
no muestra lo que hay que pagar.

## The cheapest alternative
Registrar la compra como gasto y luego una entrada de inventario. Funciona pero duplica el trabajo
y no deja deuda ni historial por proveedor. No basta.

## Goals
- Ficha de proveedor (nombre, teléfono, documento, contacto) ✔.
- Compra de mercancía: un gasto con productos que sube el stock automáticamente ✔.
- Compras a crédito y cuentas por pagar con abonos parciales ✔★.
- Historial de compras por proveedor ✔.

## Non-goals / out of scope
- Órdenes de compra, cotizaciones a proveedores, recepción parcial, devoluciones a proveedor.
- Actualizar automáticamente el costo del producto con el de la compra.

## Users & context
El dueño cuando recibe mercancía o paga a un proveedor.

## Behaviour
- Proveedores: crear, editar, eliminar (no si se le debe o él debe), con nombre obligatorio,
  teléfono, documento y nota.
- Gasto: puede llevar proveedor. Estado **Pagado** (con medio de pago) o **A crédito** (exige
  proveedor, vencimiento opcional) ★.
- Compra de mercancía: un gasto de "Compra de productos e insumos" con líneas (producto, cantidad,
  costo unitario —por defecto el costo del producto—); su valor es la suma de las líneas y cada
  línea **sube el stock** ✔. Editarla aplica la diferencia; eliminarla la revierte.
- Deuda con un proveedor = compras a crédito − abonos. El abono no supera la deuda.
- Balance de caja: los gastos a crédito no son egreso hasta que se abonan; los abonos a proveedores
  son egreso ★ ("Lo verás en Egresos"). El inicio muestra además el total por pagar.
- Cuentas por pagar: total, proveedores con deuda (mayor primero), desde cuándo y vencidas ✔.
- Historial por proveedor: todas sus compras (fecha, valor, productos) ✔.

## Acceptance criteria
- Given un proveedor y el producto "Gaseosa" con stock 5, When se registra una compra de 24 a 2.000
  pagada en efectivo, Then el gasto vale 48.000, el stock es 29 y los gastos del día suben 48.000.
- Given esa compra editada a 12 unidades, Then el stock es 17; eliminada, Then vuelve a 5.
- Given una compra a crédito de 100.000 sin proveedor, Then se rechaza; con proveedor, Then la deuda es
  100.000, los gastos del día no suben y el total por pagar es 100.000.
- Given un abono de 40.000, Then la deuda es 60.000 y los gastos del día suben 40.000; un abono de
  70.000 se rechaza.
- Given un proveedor con deuda, When se intenta eliminar, Then se rechaza.
- Given compras de dos proveedores, Then el historial de cada uno muestra solo las suyas.
- Given un proveedor de otro negocio, Then todo responde como inexistente.

## Points to clarify
- **suposición tomada** — Los abonos a proveedores aparecen como movimientos de egreso.
  *Base:* texto de la app ★. *Riesgo:* bajo.
- **decisión diferida** — Actualizar el costo del producto con el de la última compra.
