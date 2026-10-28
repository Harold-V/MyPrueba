---
type: spec
title: Spec — Deudas y fiados
description: WHAT and WHY para clientes, ventas fiadas (a crédito), abonos y cuentas por cobrar.
tags: [sdd, spec]
timestamp: 2026-10-28T06:45:00Z
topic: sdd
slug: deudas-fiados
status: approved in autopilot
---

# Spec — Deudas y fiados

> Slug: `deudas-fiados` · Status: approved in autopilot (2026-10-28) · Created: 2026-10-28
> Inherits: [constitution](../constitution.md) v1.1.0 · Depends on:
> [libro-ventas-gastos-balance](libro-ventas-gastos-balance.md) · Plataforma: [web](../../product/plataforma.md)
> Referente: Treinta (clean-room). Evidencia REA: ninguna aún; lo atribuido al referente está
> `sin verificar` (principio 22).

## Problem & why
En la tienda de barrio se fía: el cliente se lleva hoy y paga después, a veces por partes. El dueño
lo anota en un cuaderno que se pierde, se moja o no cuadra, y no sabe cuánto le deben ni quién. El
referente ofrece un módulo de deudas (`sin verificar`).

## Cost of not building it
Dinero que no se cobra porque nadie recuerda la deuda, discusiones con clientes sin un registro
claro, y un balance que no distingue lo vendido de lo realmente cobrado.

## The cheapest alternative
Anotar el fiado como venta con una nota "debe". El balance lo contaría como dinero recibido, no se
pueden registrar abonos parciales y no hay total por cliente. No basta.

## Goals
- El dueño registra una venta fiada a un cliente en el mismo flujo de una venta normal.
- Ve cuánto le debe cada cliente y el total por cobrar.
- Registra abonos parciales o totales, y cada cliente tiene su historial de fiados y abonos.
- El balance refleja el dinero realmente recibido.

## Non-goals / out of scope
- Deudas con proveedores (cuentas por pagar). Diferido.
- Recordatorios de cobro por WhatsApp o SMS, fechas de vencimiento e intereses. Diferidos.
- Abono inicial dentro de la misma venta fiada: se registra como abono aparte.
- Límite de crédito por cliente.

## Users & context
El dueño, frente a un cliente conocido del barrio que pide fiado, o recibiendo un abono. Necesita
encontrar al cliente rápido y ver su saldo.

## Behaviour
- Main path — clientes: crear, editar y eliminar clientes con nombre (obligatorio), teléfono y nota.
- Main path — fiado: en una venta (libre o con productos), el método de pago "Fiado" exige un
  cliente. Cualquier venta puede llevar cliente aunque se pague de contado.
- Main path — deuda: deuda de un cliente = total de sus ventas fiadas − sus abonos.
- Main path — abono: valor, método de pago (no "Fiado"), fecha y nota opcional; reduce la deuda.
- Main path — cuentas por cobrar: total por cobrar y lista de clientes con deuda, de mayor a menor.
- Main path — cuenta del cliente: sus ventas fiadas y abonos, del más reciente al más antiguo.
- Main path — balance: ingresos = ventas pagadas + abonos recibidos; las ventas fiadas no son
  ingreso hasta que se abonan. Los movimientos muestran fiados y abonos.
- Edge: un abono no puede superar la deuda actual del cliente.
- Edge: editar o eliminar una venta fiada o un abono recalcula la deuda; si queda negativa se
  muestra como saldo a favor.
- Edge: un cliente con deuda distinta de cero no se puede eliminar; uno sin deuda sí, y sus ventas y
  abonos conservan su nombre.
- Edge: los gastos no admiten "Fiado".
- Error: clientes de otro negocio o inexistentes responden como "no existe".

## Acceptance criteria
- Given un cliente "Juan", When se registra una venta fiada de 20.000 a Juan, Then su deuda es 20.000,
  el total por cobrar es 20.000 y las ventas del balance no suben.
- Given una venta con método "Fiado" sin cliente, Then se rechaza indicando el cliente.
- Given la deuda de 20.000, When Juan abona 5.000 en efectivo, Then su deuda es 15.000 y los ingresos
  del día suben 5.000.
- Given la deuda de 15.000, When se intenta un abono de 16.000, Then se rechaza indicando el valor.
- Given ventas fiadas y abonos, Then la cuenta de Juan lista ambos del más reciente al más antiguo.
- Given la deuda de 15.000, When se elimina la venta fiada, Then la deuda es −5.000 (saldo a favor).
- Given Juan con deuda, When se intenta eliminarlo, Then se rechaza; Given deuda 0, Then se elimina y
  sus ventas siguen mostrando "Juan".
- Given un gasto con método "Fiado", Then se rechaza.
- Given dos negocios A y B, When A intenta ver, editar, eliminar, fiar o abonar a un cliente de B,
  Then responde como inexistente y nada de B cambia.

## Points to clarify
- **suposición tomada** — El balance es de caja: el fiado no es ingreso hasta el abono. *Base:* la
  pregunta del dueño es "¿cuánto dinero me quedó?". *Riesgo:* si se quiere ver lo vendido, se añade
  una cifra de ventas totales; cambia la definición de "Ventas" del balance.
- **suposición tomada** — "Fiado" es un método de pago más. *Base:* encaja en el flujo de venta
  existente sin pasos extra. *Riesgo:* bajo.
- **decisión diferida** — Cuentas por pagar a proveedores.
- **área no formulable** — Cómo se compartirá el estado de cuenta con el cliente.

## Revisions
- 2026-10-28 — Este módulo cambia el balance de [libro-ventas-gastos-balance](libro-ventas-gastos-balance.md):
  las ventas fiadas no suman a "Ventas" y los abonos suman como ingreso.
