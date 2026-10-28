---
type: spec
title: Spec — Alineación con Treinta (fase 1)
description: Ajustar ventas, gastos, negocio e inventario al comportamiento verificado con REA.
tags: [sdd, spec]
timestamp: 2026-10-28T19:10:00Z
topic: sdd
slug: alineacion-treinta
status: approved in autopilot
---

# Spec — Alineación con Treinta (fase 1)

> Slug: `alineacion-treinta` · Status: approved in autopilot (2026-10-28) · Inherits: constitution v1.1.0
> Evidencia: [HAR REA](../../research/treinta-har-2026-10-28.md) (✔ = verificado ahí) ·
> [funcionalidades públicas](../../research/treinta-funcionalidades.md) · [hoja de ruta](../roadmap.md) fase 1.

## Problem & why
Los módulos ya construidos se basaron en suposiciones `sin verificar`. La evidencia REA muestra
diferencias concretas que un usuario de Treinta notaría: faltan sus medios de pago (Nequi,
Daviplata), las categorías de gasto y los tipos de negocio no coinciden, el fiado no tiene fecha de
vencimiento y el stock es opcional cuando en Treinta todo producto lo tiene.

## Cost of not building it
La réplica se siente distinta desde el primer registro, y las fases siguientes (proveedores,
reportes) se construirían sobre un modelo de fiado y stock que habría que rehacer.

## The cheapest alternative
Solo renombrar textos en la interfaz. Corrige lo visible pero no la fecha de vencimiento, el stock
universal ni la política de stock negativo, que cambian comportamiento. No basta.

## Goals
- Medios de pago, categorías de gasto y tipos de negocio iguales a los de Treinta en Colombia.
- El fiado es un **estado** de la venta (pagada o a crédito) con fecha de vencimiento opcional.
- Todo producto tiene stock y stock mínimo, y el negocio decide si permite vender sin stock.

## Non-goals / out of scope
- Gastos a crédito y proveedores (fase 3). Pago dividido entre varios medios. Link de pago real
  (solo se registra como medio). Impuestos, categorías y variantes de producto (fase 4).

## Users & context
El mismo dueño, que puede venir de usar Treinta y espera encontrar las mismas opciones.

## Behaviour
- Medios de pago de venta, gasto y abono: Efectivo, Tarjeta, Transferencia bancaria, Nequi,
  Daviplata, Link de pago, Otro ✔.
- Venta: estado **Pagada** (con medio de pago) o **A crédito / fiado** (sin medio de pago, con
  cliente obligatorio y fecha de vencimiento opcional, que no puede ser anterior a la venta) ✔.
- Cuentas por cobrar muestran por cliente la deuda, la venta fiada más antigua sin saldar
  ("desde cuándo") y si hay fiados vencidos.
- Categorías de gasto (10): Servicios públicos; Compra de productos e insumos; Arriendo; Nómina;
  Gastos administrativos; Mercadeo y publicidad; Transporte, domicilios y logística; Mantenimiento
  y reparaciones; Muebles, equipos o maquinaria; Otros ✔. Los gastos existentes en "Impuestos" pasan a "Otros".
- Un gasto sin concepto recibe uno automático "Gasto ddMMaaHHmmss" en la hora del negocio ✔.
- Tipos de negocio: los 59 de Treinta en 3 grupos (comercio; restaurantes y licores; servicios) ✔.
  Los negocios existentes se convierten al tipo equivalente.
- Todo producto tiene stock (puede empezar en 0) y stock mínimo opcional; se crean con stock
  inicial ✔. Ya no existe "activar inventario".
- Configuración del negocio: "Permitir vender sin stock" (por defecto sí ✔). Si está desactivada,
  una venta que deja un producto en negativo se rechaza indicando el producto.
- Edge: una venta editada solo se valida contra el stock que ella misma no tenía antes.

## Acceptance criteria
- Given una venta pagada con Nequi, Then se guarda y figura como "Nequi" en movimientos.
- Given una venta "A crédito" sin cliente, Then se rechaza; con cliente y vencimiento, Then crea
  deuda con esa fecha.
- Given un vencimiento anterior a la fecha de la venta, Then se rechaza.
- Given un fiado vencido sin saldar, Then cuentas por cobrar lo marca como vencido.
- Given un gasto sin concepto el 28/10/2026 03:14:23 en Bogotá, Then su concepto es "Gasto 281026031423".
- Given un producto creado con stock 10, Then el inventario muestra 10 sin pasos adicionales.
- Given "Permitir vender sin stock" desactivado y stock 2, When se venden 3, Then se rechaza
  indicando el producto; When está activado, Then queda en −1.
- Given un negocio con tipo antiguo "Tienda / abarrotes", Then tras la migración es "Tienda de barrio".

## Points to clarify
- **suposición tomada** — Al desactivar "Permitir vender sin stock" Treinta bloquea la venta.
  *Base:* el nombre del ajuste `stockBehavior: ALLOW_NEGATIVE` implica una alternativa que no
  permite. *Riesgo:* si Treinta solo avisa, se cambia el rechazo por aviso.
- **suposición tomada** — "desde cuándo" = fecha de la venta fiada más antigua con saldo.
  *Base:* página pública de deudas. *Riesgo:* bajo.
- **decisión diferida** — Pago dividido entre medios de pago.
