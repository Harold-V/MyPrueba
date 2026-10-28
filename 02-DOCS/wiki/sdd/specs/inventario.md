---
type: spec
title: Spec — Inventario
description: WHAT and WHY para controlar el stock de productos, que baja con las ventas, con ajustes, historial y alertas de stock bajo.
tags: [sdd, spec]
timestamp: 2026-10-28T06:10:00Z
topic: sdd
slug: inventario
status: approved in autopilot
---

# Spec — Inventario

> Slug: `inventario` · Status: approved in autopilot (2026-10-28) · Created: 2026-10-28
> Inherits: [constitution](../constitution.md) v1.1.0 · Depends on:
> [libro-ventas-gastos-balance](libro-ventas-gastos-balance.md) · Plataforma: [web](../../product/plataforma.md)
> Referente: Treinta (clean-room). Evidencia REA: ninguna aún; lo atribuido al referente está
> `sin verificar` (principio 22).

## Problem & why
El dueño no sabe cuánto le queda de cada producto. Se entera de que algo se acabó cuando un cliente
lo pide, y pierde la venta. Tampoco sabe cuánto dinero tiene invertido en mercancía. Contar a mano
cada noche no es viable. El referente ofrece inventario ligado a las ventas (`sin verificar`).

## Cost of not building it
Ventas perdidas por productos agotados, compras a ciegas, y el dueño sigue usando un cuaderno
aparte para el stock, que se desincroniza con las ventas que ya registra en la app.

## The cheapest alternative
Una columna "cantidad" editable a mano en cada producto. Es barata, pero obliga a restar a mano tras
cada venta, que es justo el trabajo que el dueño no hace, y no deja rastro de por qué cambió el número.
No basta.

## Goals
- El stock de un producto baja solo al venderlo y se corrige solo al editar o eliminar la venta.
- El dueño registra entradas (compras), pérdidas y conteos, y ve el historial de cada producto.
- El dueño ve de un vistazo qué productos están en stock bajo y cuánto vale su inventario.

## Non-goals / out of scope
- Registrar automáticamente un gasto al cargar mercancía. Diferido.
- Proveedores, órdenes de compra, lotes, vencimientos, variantes o bodegas. Diferidos.
- Utilidad por venta o reportes de rentabilidad. Diferidos a reportes.
- Bloquear una venta por falta de stock.
- Códigos de barras e importación masiva de productos.

## Users & context
El dueño de [cuentas-y-negocio](cuentas-y-negocio.md), en el celular durante el día y en el
computador al cerrar o al recibir mercancía. Controla el stock solo de los productos que le importan.

## Behaviour
- Main path — activar: el dueño activa el control de inventario de un producto indicando el stock
  actual y, opcionalmente, un stock mínimo. Puede desactivarlo; el historial se conserva.
- Main path — costo: cada producto puede tener un costo unitario opcional.
- Main path — venta: al registrar una venta con productos controlados, su stock baja en la cantidad
  vendida. Editarla aplica solo la diferencia; eliminarla devuelve las cantidades.
- Main path — ajustes: entrada (suma), pérdida (resta) o conteo (fija el valor contado), con nota
  opcional.
- Main path — historial: por producto, los movimientos de stock del más reciente al más antiguo, con
  tipo, cambio, stock resultante, fecha y nota.
- Main path — vista de inventario: lista de productos con su stock, mínimo y estado; resumen con
  productos controlados, cuántos están en stock bajo y el valor del inventario al costo.
- Main path — alertas: el inicio avisa cuando hay productos en stock bajo y enlaza al inventario; al
  vender se muestra el stock disponible.
- Edge: stock bajo = stock controlado menor o igual al mínimo, o negativo.
- Edge: una venta puede dejar el stock en negativo; no se bloquea, se marca.
- Edge: los productos sin control de inventario y las líneas sin producto no mueven stock.
- Edge: activar el control después de una venta no cambia el stock por ventas anteriores.
- Edge: el valor del inventario suma stock × costo solo de productos con costo y stock positivo.
- Edge: cantidades con hasta 3 decimales; el conteo admite cero.
- Error: cantidades inválidas indican el campo; productos ajenos o inexistentes responden como
  "no existe".

## Acceptance criteria
- Given "Gaseosa" con inventario activado en 10, When se vende 2, Then su stock es 8 y el historial
  muestra una salida por venta de −2 con stock resultante 8.
- Given esa venta, When se edita a 5 unidades, Then el stock es 5; When se elimina, Then el stock vuelve a 10.
- Given una venta que cambia de "Gaseosa" a "Pan" al editarse, Then Gaseosa recupera sus unidades y Pan las pierde.
- Given un producto sin control de inventario, When se vende, Then no aparece movimiento de stock.
- Given stock 8, When el dueño registra una entrada de 12, Then el stock es 20; When registra una
  pérdida de 1, Then es 19; When registra un conteo de 15, Then es 15 y el cambio registrado es −4.
- Given stock 3 y mínimo 5, Then el producto figura en stock bajo y el inicio muestra el aviso.
- Given stock 1, When se venden 3, Then la venta se registra y el stock queda en −2, marcado.
- Given stock 4 con costo 2.500 y stock 2 sin costo, Then el valor del inventario es 10.000.
- Given una cantidad 0 en una entrada o pérdida, or −1 en un conteo, Then se rechaza indicando el campo.
- Given dos negocios A y B, When A intenta ver, configurar o ajustar el inventario de un producto de B,
  Then responde como inexistente y nada de B cambia.

## Points to clarify
- **suposición tomada** — La venta no se bloquea por falta de stock; queda en negativo y marcada.
  *Base:* en tienda, bloquear una venta real por un inventario desactualizado es peor que el
  desfase. *Riesgo:* si se pide bloquear, cambia el criterio de stock negativo.
- **suposición tomada** — El costo es un valor único por producto (último costo), sin promedio
  ponderado. *Base:* simplicidad para micronegocio. *Riesgo:* el valor del inventario es aproximado si
  el costo cambia mucho.
- **suposición tomada** — Al eliminar un producto se elimina su stock e historial. *Base:* el producto
  deja de existir; las ventas guardan su copia. *Riesgo:* bajo.
- **decisión diferida** — Generar un gasto al registrar una entrada de mercancía.
- **área no formulable** — Cómo se integrará el inventario con proveedores y compras a crédito.
