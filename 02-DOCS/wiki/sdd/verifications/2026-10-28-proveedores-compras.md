---
type: verification
title: Verify — proveedores, compras y cuentas por pagar (fase 3)
tags: [sdd, verify]
topic: sdd
---

# Verify — proveedores, compras y cuentas por pagar (fase 3)

> Fecha: 2026-10-28 · Veredicto: **PASS**

- Gate `scripts/verify.sh` exit 0: backend 127/127 (9 nuevas en `SupplierApiTest` y
  `PayablesApiTest`; `expensesDoNotAcceptCredit` pasa a `creditExpensesNeedASupplier` porque la spec
  cambia esa regla), frontend 71/71 (10 nuevas en `suppliers.test.tsx`), 112,6 KB gzip,
  0 vulnerabilidades. `ApplicationModules.verify()` pasa con el módulo nuevo `suppliers`.
- Criterios (todos con prueba en `PayablesApiTest`):
  - compra pagada de 24 × 2.000 → gasto 48.000, stock 5 → 29, gastos del día 48.000;
  - editada a 12 → stock 17; eliminada → 5;
  - a crédito sin proveedor → 400 `supplierId`; con proveedor → deuda y por pagar 100.000, gastos 0;
  - abono 40.000 → deuda 60.000, gastos 40.000; abono 70.000 → `exceeds_debt`;
  - borrar proveedor con deuda → 409 `supplier_has_debt`;
  - historial por proveedor solo con sus compras ("10 × Gaseosa" / concepto);
  - otro negocio → 404 en proveedor, abonos, cuenta e historial; por pagar 0.
- Migración: V7 aplicada sobre la base de la fase 2 (con datos) sin errores.
- Navegador (Chromium, 1280 px y 360 px): producto Gaseosa (costo 2.000, stock 5) → compra con
  productos de 24, a crédito, con "Nuevo proveedor" Distribuidora Andina → inicio muestra "A crédito
  con Distribuidora Andina" y "Por pagar: $48.000" → inventario 29 → proveedor "Le debes $48.000" →
  pago 20.000 → "Le debes $28.000", gastos $20.000, balance −$20.000. En móvil: 5 pestañas (Inicio,
  Productos, Inventario, Clientes, Proveedores), "Mi negocio" en la cabecera, sin scroll horizontal.
  Sin errores 5xx.
- Límite conocido: revertir o reducir una compra puede dejar stock negativo aunque el negocio no
  permita vender sin stock; la política solo frena ventas (igual que el ajuste "Pérdida").
