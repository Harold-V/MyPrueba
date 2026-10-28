---
type: verification
title: Verify — deudas y fiados
tags: [sdd, verify]
topic: sdd
---

# Verify — deudas y fiados

> Fecha: 2026-10-28 · Constitución v1.1.0 · Veredicto: **PASS**

## Gate (`scripts/verify.sh`, exit 0)

| Comprobación | Resultado |
|---|---|
| Tests backend | 104/104 verdes (3 de clientes, 11 de cuentas por cobrar) |
| `ApplicationModules.verify()` | pasa (ledger → customers; customers no depende de ledger) |
| Cobertura de líneas | backend 98,9 % · frontend 93,8 % |
| Tests frontend | 56/56 verdes |
| Lint, formato, `tsc` strict | limpios |
| JS inicial gzip | 107,1 KB (máximo 250 KB) |
| `npm audit --omit=dev` | 0 vulnerabilidades |

## Criterios de aceptación

Los 9 criterios tienen prueba en `ReceivablesApiTest`, `CustomerApiTest` y `customers.test.tsx`:
fiado crea deuda sin sumar ingreso; fiado exige cliente; abono reduce deuda y suma ingreso; abono no
supera la deuda; cuenta ordenada; borrar fiado deja saldo a favor; cliente con deuda no se borra
(sin deuda sí, y conserva el nombre); gastos sin fiado; aislamiento A/B.

## Prueba en la app real (Chromium, 1280 px y 360 px)

Dos clientes → venta fiada de $20.000 a Juan → "Debe $20.000" → abono de $5.000 → "Debe $15.000" →
eliminar Juan rechazado con aviso junto al botón → inicio: Ventas $0, Balance $5.000,
"Abonos: $5.000 · Por cobrar: $15.000". Sin errores 5xx ni scroll horizontal.

## Hallazgos corregidos durante verify

1. El aviso de "no se puede eliminar" quedaba fuera de la vista: ahora aparece junto al botón.
2. Con cinco secciones, la barra inferior del celular amontonaba las etiquetas: letra más pequeña en móvil.
3. El orden de clientes por deuda usaba `Number`: ahora usa centavos exactos (principio 23).
4. Una prueba de componente dependía del tiempo bajo carga: ahora espera el botón de confirmación.

## Riesgos aceptados

- La lista de clientes y la de cuentas no tienen paginación.
- Un abono de un cliente eliminado no se puede editar, solo eliminar.
