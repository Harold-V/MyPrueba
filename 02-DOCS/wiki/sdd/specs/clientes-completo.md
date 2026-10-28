---
type: spec
title: Spec — Clientes completos (fase 2)
description: Ficha completa del cliente, historial de compras y alta desde la venta.
tags: [sdd, spec]
timestamp: 2026-10-28T19:40:00Z
topic: sdd
slug: clientes-completo
status: approved in autopilot
---

# Spec — Clientes completos (fase 2)

> Slug: `clientes-completo` · Status: approved in autopilot (2026-10-28) · Inherits: constitution v1.1.0
> Fuente: [página pública de clientes](https://www.treinta.co/programa-para-manejar-base-de-datos-de-clientes/)
> (✔ = descrito ahí) · [brecha](../../research/treinta-funcionalidades.md) · [hoja de ruta](../roadmap.md) fase 2.

## Problem & why
La ficha del cliente solo tiene nombre y teléfono, y su cuenta solo muestra fiados y abonos. El
dueño no ve qué le ha comprado un cliente de contado ni puede guardar su documento y dirección, que
necesitará para comprobantes. Además, registrar un cliente obliga a salir de la venta.

## Cost of not building it
No se identifica a los mejores clientes, y en la caja se pierde la venta mientras se busca otra pantalla.

## The cheapest alternative
Anotar documento y dirección en la nota del cliente. No sirve para comprobantes ni búsquedas.

## Goals
- Ficha con nombre, teléfono, documento y dirección ✔.
- Historial de compras por cliente: cuánto, cuándo y qué productos ✔.
- Crear el cliente desde la venta sin salir del flujo ✔.

## Non-goals / out of scope
- Comprobantes con datos del cliente (fase 6). Segmentación, mensajes y campañas.

## Users & context
El dueño en la caja (alta rápida) y al revisar a sus clientes frecuentes.

## Behaviour
- Ficha: nombre (obligatorio), teléfono, documento (hasta 30 caracteres) y dirección (hasta 200).
- Historial: todas las ventas del cliente (pagadas y fiadas), de la más reciente a la más
  antigua, con fecha, total, estado y descripción (productos o concepto); y resumen con total
  comprado y número de compras.
- Desde la venta: "Nuevo cliente" pide nombre y teléfono, lo crea y lo deja seleccionado ✔.

## Acceptance criteria
- Given un cliente con documento y dirección, Then su ficha los muestra y se pueden editar.
- Given dos ventas a Juan (una pagada de 10.000 y una fiada de 5.000), Then su historial lista ambas
  con total comprado 15.000 y 2 compras; las ventas de otros clientes no aparecen.
- Given la venta abierta, When se crea "Pedro" desde ella, Then Pedro queda seleccionado y la venta
  se guarda a su nombre.
- Given un cliente de otro negocio, When se pide su historial, Then responde como inexistente.

## Points to clarify
- **suposición tomada** — El documento es texto libre (cédula, NIT, pasaporte), sin validar dígito.
  *Base:* la página no especifica tipos. *Riesgo:* la facturación electrónica exigirá tipo y validación.
