---
type: spec
title: Spec — Sistema de ventas (POS, descuentos, comprobante y caja)
description: Punto de venta rápido, descuentos por producto y por venta, comprobante digital numerado y apertura/cierre de caja.
tags: [sdd, spec]
timestamp: 2026-10-28T22:00:00Z
topic: sdd
slug: sistema-ventas
status: approved in autopilot
---

# Spec — Sistema de ventas

> Slug: `sistema-ventas` · Status: approved in autopilot (2026-10-28; alcance elegido por el dueño:
> POS, descuentos, comprobante digital y caja) · Inherits: constitution v1.1.0 · Absorbe la fase 6
> (`ventas-avanzadas`) de la hoja de ruta.
> Fuentes: [registro de ventas y gastos](https://www.treinta.co/registro-ventas-gastos/) (✔ =
> descrito ahí: "registro rápido de ventas", "descuentos para ventas", "comprobantes digitales",
> "flujo de caja en tiempo real") y [HAR](../../research/treinta-har-2026-10-28.md) (★ = observado:
> totales con subtotal y descuento, pantalla de confirmación de venta, caja con cierre y
> diferencias).

## Problem & why
Vender con el formulario actual es lento en el mostrador: hay que elegir producto por producto en
una lista, no se puede dar descuento, el cliente no recibe soporte de su compra y al final del día
el dueño no sabe si el efectivo de la caja cuadra.

## Cost of not building it
Filas en el mostrador, descuentos anotados aparte (o regalados sin registro), clientes sin
comprobante y faltantes de caja que nadie detecta.

## The cheapest alternative
Seguir con el formulario y cuadrar la caja en un cuaderno. Funciona con pocas ventas; con
movimiento real se pierde tiempo y dinero. No basta.

## Goals
- Punto de venta: vender en pocos toques con catálogo, buscador, lector de códigos y carrito ✔.
- Descuentos por producto y sobre el total, en valor o porcentaje ✔★.
- Comprobante digital numerado de cada venta, para imprimir, guardar en PDF o enviar por WhatsApp ✔.
- Caja: apertura con base, efectivo esperado en tiempo real y cierre con conteo y diferencia ✔★.

## Non-goals / out of scope
- Factura electrónica DIAN, impuestos por producto (IVA/impoconsumo), propinas y domicilios.
- Enlace público del comprobante (exigiría páginas sin sesión); se comparte como texto o PDF.
- Varias cajas o turnos por empleado (llega con empleados, fase 7).
- Que la diferencia de caja cree un ingreso o gasto automático.

## Users & context
El dueño o quien atiende el mostrador, en celular o computador, con o sin lector de códigos.

## Behaviour
### Punto de venta
- Pantalla propia: productos en tarjetas (nombre, precio, stock) filtrables por categoría y por
  texto; tocar una tarjeta la suma al carrito (otra vez = +1). Un código exacto + Enter la suma.
- Carrito: cantidades con − y +, quitar línea, descuento por línea, descuento total, subtotal,
  descuentos y total siempre visibles. En celular el carrito es un panel que se abre desde una
  barra con el total; en escritorio está fijo a la derecha.
- Cobro: Pagada (medio de pago; en efectivo, "con cuánto paga" y el cambio) o Fiada (cliente
  obligatorio, vencimiento opcional). Cliente opcional en pagadas.
- Al cobrar se muestra el comprobante y el botón "Nueva venta"; el aviso de stock insuficiente
  sigue la política del negocio.

### Descuentos
- Por línea y por venta, en valor ($) o porcentaje (0–100 %, hasta 2 decimales). El porcentaje se
  redondea a centavos (mitad hacia arriba).
- Un descuento no puede superar el valor sobre el que se aplica. Solo en ventas con productos.
- La venta guarda subtotal, descuentos y total; el total es lo que cuenta en balance, deudas y
  reportes. El formulario de venta también permite descuentos.

### Comprobante
- Cada venta tiene un número consecutivo por negocio (las anteriores se numeran por fecha de
  creación).
- Muestra: datos del negocio (nombre, NIT o documento, teléfono, dirección — se editan en
  Negocio), número, fecha y hora, cliente (nombre, documento, teléfono), líneas con descuentos,
  subtotal, descuentos, total, medio de pago, recibido y cambio, o "Fiado" con vencimiento.
- Acciones: imprimir/guardar PDF y enviar por WhatsApp (texto del comprobante, al teléfono del
  cliente si lo tiene).

### Caja
- Abrir con una base en efectivo (puede ser 0). Solo una caja abierta a la vez.
- Mientras está abierta: efectivo esperado = base + ventas pagadas en efectivo + abonos de clientes
  en efectivo − gastos pagados en efectivo − pagos a proveedores en efectivo, registrados desde la
  apertura. Se muestra el desglose.
- Cerrar contando el efectivo: se guarda esperado, contado y diferencia (sobrante + / faltante −) con
  nota opcional. Historial de cierres.
- Vender no exige caja abierta.

## Acceptance criteria
- Given Gaseosa a 3.000 y Pan a 500, When se vende 2 Gaseosas con 10 % de descuento en la línea y 4
  panes, con 500 de descuento total, Then subtotal 8.000, descuentos 1.100 y total 6.900; el
  balance suma 6.900.
- Given un descuento de 9.000 sobre una línea de 6.000, Then se rechaza indicando la línea.
- Given una venta en efectivo de 6.900 con recibido 10.000, Then el comprobante dice cambio 3.100;
  con recibido 5.000, Then se rechaza.
- Given dos ventas nuevas, Then sus números son consecutivos; una venta de otro negocio no altera
  la secuencia.
- Given el comprobante de una venta fiada a Juan, Then incluye los datos del negocio y de Juan,
  "Fiado" y el vencimiento.
- Given una caja abierta con base 50.000, una venta en efectivo de 6.900, una en tarjeta de 10.000,
  un abono en efectivo de 5.000 y un gasto en efectivo de 2.000, Then el esperado es 59.900; al
  cerrar contando 59.000, Then la diferencia es −900 y la caja queda cerrada.
- Given una caja abierta, When se intenta abrir otra, Then se rechaza; cerrar sin caja abierta se
  rechaza.
- Given datos de otro negocio, Then comprobantes y cajas responden como inexistentes.

## Points to clarify
- **suposición tomada** — El efectivo esperado cuenta los movimientos **registrados** mientras la
  caja está abierta (no su fecha de venta), porque es cuando el dinero entró al cajón.
  *Riesgo:* bajo.
- **suposición tomada** — La diferencia de caja se guarda en el cierre y no crea movimientos.
  *Base:* el HAR muestra transacciones de diferencia, pero crearlas alteraría el balance sin que
  el dueño lo pida. *Riesgo:* medio; se puede añadir después.
- **suposición tomada** — Descuentos solo en ventas con productos (una venta libre ya es el valor
  final). *Riesgo:* bajo.
