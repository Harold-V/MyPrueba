---
type: verification
title: Verify — alineación con Treinta (fase 1)
tags: [sdd, verify]
topic: sdd
---

# Verify — alineación con Treinta (fase 1)

> Fecha: 2026-10-28 · Constitución v1.1.0 · Veredicto: **PASS**

## Gate (`scripts/verify.sh`, exit 0)
Backend 115/115 (9 de alineación, 13 de inventario reescritas, 1 de migración V5), `verify()` OK,
cobertura ≥ 80 %. Frontend 59/59, 94,0 % de líneas, lint y tipos limpios, 109 KB gzip, 0 vulnerabilidades.

## Criterios de aceptación
Los 8 criterios tienen prueba (`AlignmentApiTest`, `InventoryApiTest.sellingWithoutStockCanBeBlocked`,
`V5MigrationTest`, pruebas de componente): Nequi/Daviplata/Link de pago; fiado como estado que exige
cliente y acepta vencimiento; vencimiento anterior rechazado; "desde cuándo" con abonos aplicados a
lo más antiguo y fiado vencido; concepto automático "Gasto ddMMaaHHmmss"; stock inicial al crear;
bloqueo de venta sin stock con nombre del producto; migración de tipos de negocio, gastos en
"Impuestos" y productos sin fila de stock.

## Navegador (Chromium 1280 px)
Ferretería con tipo "Ferretería y construcción" → producto con stock 2 → "Permitir vender sin
stock" desactivado → venta de 3 rechazada con aviso junto al botón → venta fiada a Juan con
vencimiento → gasto Daviplata sin concepto = "Gasto 281026140538" → clientes: "Debe desde el 28 de
oct de 2026". Sin errores 5xx.

## Hallazgos corregidos
1. El aviso de stock decía "se registrará igual" aun con el ajuste desactivado.
2. El error del formulario quedaba fuera de la vista: ahora va junto al botón Guardar.

## Nota sobre la evidencia
En el HAR, el concepto automático usaba la hora UTC del momento (08:14 para las 03:14 de Bogotá);
puede ser la zona del navegador de la grabación. Usamos la hora del negocio. A confirmar con otra grabación.
