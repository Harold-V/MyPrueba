---
type: spec
title: Spec — Libro de ventas, gastos y balance
description: WHAT and WHY para registrar ventas (libres o con productos), gastos y ver el balance por periodo.
tags: [sdd, spec]
timestamp: 2026-10-28T05:30:00Z
topic: sdd
slug: libro-ventas-gastos-balance
status: approved in autopilot
---

# Spec — Libro de ventas, gastos y balance

> Slug: `libro-ventas-gastos-balance` · Status: approved in autopilot (2026-10-28) · Created: 2026-10-28
> Inherits: [constitution](../constitution.md) v1.0.0 · Depends on: [cuentas-y-negocio](cuentas-y-negocio.md)
> Referente: Treinta (réplica funcional clean-room). Evidencia REA: ninguna aún — todo comportamiento
> atribuido a Treinta está `sin verificar` (principio 22).

## Problem & why
El dueño de un micronegocio no sabe si gana o pierde dinero: anota en un cuaderno (o no anota),
mezcla el dinero del negocio con el personal y no puede responder "¿cuánto vendí hoy?" ni "¿cuánto
me quedó este mes?". El libro diario de ventas y gastos con su balance es el núcleo del referente
(`sin verificar`) y la base de reportes, deudas e inventario.

## Cost of not building it
El producto no cumple su promesa principal. El dueño sigue sin saber su ganancia y no hay datos
sobre los que construir reportes, deudas ni inventario.

## The cheapest alternative
Una hoja de cálculo plantilla. Resuelve la suma, pero en el celular es lenta de llenar, no sugiere
productos ni precios, se rompe con una fórmula borrada y no se integra con módulos futuros. Útil
como respaldo, no como producto.

## Goals
- Registrar una venta o un gasto toma pocos toques desde el celular.
- El dueño ve en todo momento el total de ventas, el total de gastos y el balance del periodo elegido.
- Una venta puede hacerse con productos guardados, sin reescribir nombre y precio cada vez.

## Non-goals / out of scope
- Control de inventario o stock (módulo inventario). Los productos aquí solo tienen nombre y precio.
- Ventas a crédito / fiados y abonos (módulo deudas). Toda venta aquí está pagada.
- Clientes y proveedores asociados a movimientos. Diferido.
- Ganancia por costo de producto (utilidad bruta). Diferido.
- Reportes descargables, gráficas y comparativas entre periodos. Diferido.
- Recibos o comprobantes para el cliente, facturación electrónica DIAN. Diferidos.
- Categorías de gasto personalizadas, fotos de productos, códigos de barras, varias monedas.

## Users & context
El mismo dueño de [cuentas-y-negocio](cuentas-y-negocio.md), con sesión iniciada, en medio de la
atención a clientes: necesita anotar rápido y consultar el balance al cerrar el día.

## Behaviour
- Main path — productos: el dueño crea, edita y elimina productos con nombre y precio de venta.
- Main path — venta libre: indica valor, concepto opcional, método de pago, fecha (hoy por defecto)
  y nota opcional.
- Main path — venta con productos: elige uno o varios productos con su cantidad; el total se calcula
  como la suma de cantidad × precio; puede ajustar el precio unitario de esa venta sin cambiar el
  producto.
- Main path — gasto: indica valor, categoría, concepto opcional, método de pago, fecha (hoy por
  defecto) y nota opcional.
- Main path — movimientos: ve la lista de ventas y gastos del periodo (hoy, esta semana, este mes o
  un rango de fechas), del más reciente al más antiguo, filtrable por tipo (ventas, gastos, todos).
- Main path — balance: para el periodo elegido ve total de ventas, total de gastos y balance (ventas
  − gastos), que puede ser negativo.
- Main path — editar y eliminar: cualquier venta o gasto puede corregirse o eliminarse; el balance
  se actualiza.
- Edge: valores mayores que cero, con hasta 2 decimales, y hasta 999.999.999.999,99.
- Edge: cantidades de producto mayores que cero, con hasta 3 decimales (venta por peso).
- Edge: la fecha de un movimiento no puede ser futura.
- Edge: cambiar el precio o eliminar un producto no altera las ventas ya registradas: cada venta
  conserva nombre, precio y cantidad tal como se vendió.
- Edge: al editar una venta cuyo producto fue eliminado, sus líneas se conservan con el nombre y
  precio guardados y pueden corregirse; nunca desaparecen en silencio.
- Edge: los periodos se calculan en la zona horaria del negocio; la semana va de lunes a domingo.
- Edge: un periodo sin movimientos muestra totales en cero y un estado vacío que invita a registrar.
- Edge: los totales son exactos al centavo, sin errores de redondeo, con cualquier cantidad de
  movimientos.
- Error: datos inválidos muestran el campo y la razón sin perder lo escrito.
- Error: un movimiento o producto de otro negocio, o inexistente, responde igual que "no existe".

## Acceptance criteria
- Given un negocio sin movimientos, When el dueño abre el balance de hoy, Then ve ventas 0, gastos 0,
  balance 0 y un mensaje para registrar su primera venta.
- Given el dueño, When registra una venta libre de 15.000 en efectivo, Then aparece en movimientos de
  hoy y el total de ventas de hoy sube 15.000.
- Given los productos "Gaseosa" a 3.000 y "Pan" a 500, When registra una venta con 2 gaseosas y 4
  panes, Then la venta vale 8.000 y lista ambos productos con su cantidad y precio.
- Given una venta de "Gaseosa" a 3.000, When luego el producto cambia a 3.500 o se elimina, Then la
  venta registrada sigue mostrando "Gaseosa" a 3.000.
- Given el dueño, When registra un gasto de 50.000 en la categoría arriendo, Then el total de gastos
  sube 50.000 y el balance baja 50.000.
- Given ventas por 100.000 y gastos por 130.000 en el mes, When ve el balance del mes, Then el balance
  es −30.000.
- Given una venta con un producto que luego se eliminó, When el dueño edita la venta y la guarda,
  Then la línea sigue en la venta con su nombre y precio.
- Given 3 ventas de 0,10, When ve el total, Then es exactamente 0,30.
- Given un valor de 0, negativo o con 3 decimales, When intenta guardar una venta o gasto, Then se
  rechaza indicando el campo.
- Given una fecha de mañana, When intenta guardar un movimiento, Then se rechaza.
- Given una venta registrada el domingo a las 23:30 hora de Colombia, When ve "esta semana" el lunes
  siguiente, Then esa venta no aparece (pertenece a la semana anterior).
- Given una venta, When el dueño la edita o la elimina, Then movimientos y balance reflejan el cambio.
- Given el filtro "gastos", When ve movimientos, Then solo aparecen gastos.
- Given dos negocios A y B, When A intenta ver, editar o eliminar un producto, venta o gasto de B,
  Then recibe la misma respuesta que para uno inexistente y nada de B cambia.

## Points to clarify
- **suposición tomada** — Métodos de pago: efectivo, tarjeta, transferencia, otro. *Base:* medios
  habituales en Colombia (Nequi/Daviplata cuentan como transferencia). *Riesgo:* si se piden
  billeteras separadas, crece la lista; no cambia otros criterios.
- **suposición tomada** — Categorías de gasto fijas: compra de productos/insumos, arriendo, servicios
  públicos, nómina, transporte, mantenimiento, impuestos, otros. *Base:* gastos típicos de
  micronegocio (inspirado en el referente, `sin verificar`). *Riesgo:* bajo.
- **suposición tomada** — Eliminar un movimiento es definitivo, con confirmación previa. *Base:*
  simplicidad. *Riesgo:* si se pide papelera o historial, cambia el criterio de eliminación.
- **suposición tomada** — El nombre de producto es único por negocio, sin distinguir mayúsculas.
  *Base:* evita duplicados al elegir en una venta. *Riesgo:* bajo.
- **decisión diferida** — Ventas a crédito y abonos: módulo deudas.
- **decisión diferida** — Costo del producto y utilidad: llega con inventario.
- **área no formulable** — Cómo afectará la llegada de inventario (stock) a las ventas con productos
  ya registradas.

## Revisions

- 2026-10-28 — Añadido el caso "editar una venta con producto eliminado" (edge + criterio). Lo
  encontró la revisión adversarial durante `verify`: el formulario descartaba esas líneas al editar.
