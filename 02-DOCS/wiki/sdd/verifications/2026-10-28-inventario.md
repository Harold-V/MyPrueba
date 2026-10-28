---
type: verification
title: Verify — inventario (+ diseño web de escritorio)
tags: [sdd, verify]
topic: sdd
---

# Verify — inventario

> Fecha: 2026-10-28 · Constitución v1.1.0 · Veredicto: **PASS**

## Gate (`scripts/verify.sh`, exit 0)

| Comprobación | Resultado |
|---|---|
| Tests backend | 90/90 verdes (11 nuevas de inventario, 2 de cantidades) |
| `ApplicationModules.verify()` | pasa (inventory → ledger, catalog, accounts; sin ciclos) |
| Cobertura de líneas | backend ≥ 80 % (JaCoCo OK) · frontend 94,4 % |
| Tests frontend | 47/47 verdes |
| Lint, formato, `tsc` strict | limpios |
| JS inicial gzip | 105,1 KB (máximo 250 KB) |
| `npm audit --omit=dev` | 0 vulnerabilidades |

## Criterios de aceptación (`InventoryApiTest`, `inventory.test.tsx`)

Los 10 criterios tienen prueba: venta −2 con movimiento; edición aplica diferencia y borrado restaura;
cambio de producto mueve stock entre ambos; producto no controlado sin movimientos; entrada, pérdida
y conteo (cambio −4); stock bajo y aviso en inicio; stock negativo marcado; valor 10.000; cantidades
inválidas; aislamiento A/B. Además: una venta que falla no mueve stock (misma transacción).

## Prueba en la app real (Chromium, 1280 px y 360 px)

Productos con costo → activar Gaseosa en 10 (mínimo 5) → venta de 7 (el selector muestra
"10 disponibles") → aviso "1 producto con stock bajo" en inicio → entrada de 12 → 15 en stock →
historial Entrada +12, Venta −7, Stock inicial +10 → valor al costo $37.500. Sin errores 5xx ni
scroll horizontal a 360 px.

## Riesgos aceptados

- El historial muestra los últimos 100 movimientos por producto.
- El valor del inventario usa el último costo (sin promedio ponderado), como dice la spec.
