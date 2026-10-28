---
type: verification
title: Verify — sistema de ventas (POS, descuentos, comprobante y caja)
tags: [sdd, verify]
topic: sdd
---

# Verify — sistema de ventas

> Fecha: 2026-10-28 · Veredicto: **PASS**

- Gate `scripts/verify.sh` exit 0: backend 148/148 (9 nuevas: `SalesSystemApiTest`,
  `CashRegisterApiTest`, `V9MigrationTest`), frontend 95/95 (14 nuevas: `sales.test.tsx`,
  `discount.test.ts`), cobertura frontend 91,9 % de líneas, 121,2 KB gzip, 0 vulnerabilidades.
- Criterios (con prueba): 2 gaseosas con 10 % + 4 panes − 500 → subtotal 8.000, descuentos 1.100,
  total 6.900 y balance 6.900; descuento mayor que la línea → `items[0].discount exceeds_line`;
  recibido 10.000 → cambio 3.100, recibido 5.000 → `less_than_total`; números consecutivos por
  negocio y estables al editar; comprobante fiado con datos del negocio y de Juan y vencimiento;
  caja: base 50.000 + efectivo 6.900 + abono 5.000 − gasto 2.000 = 59.900, contado 59.000 →
  diferencia −900; una sola caja abierta; aislamiento A/B en comprobante y caja.
- Migración: V9 aplicada sobre la base de la fase 4: 4 ventas numeradas por negocio sin huecos,
  sin descuentos (subtotal = total).
- Navegador (Chromium, 1280 px y 360 px): datos del negocio → productos → abrir caja con 50.000 →
  POS con lector (código + Enter), +/−, 10 % y 500 de descuento, "Cambio: $3.100" → "Cobrar
  $6.900" → comprobante #1 correcto; vista de impresión solo con el comprobante; WhatsApp con el
  texto del comprobante → caja esperada $56.900, contado $56.000 → "Faltante: $900" → inicio con
  ventas $6.900. Móvil: pestañas Inicio, Vender, Productos, Clientes, Más; barra "Ver carrito";
  carrito a pantalla completa; sin scroll horizontal. Sin errores 5xx ni de página.
- Pruebas inestables: se encontró la causa de la falla intermitente de "edits and deletes an
  existing sale" (reproducida 2/3 bajo carga): las navegaciones de React Router son transiciones y
  la prueba hacía clic en el formulario viejo aún visible. Corregida en esa prueba y en las dos con
  el mismo patrón (abonos de clientes y de proveedores); 6/6 bajo la misma carga.
- Pendiente (no bloquea): la diferencia de caja no crea un movimiento de ingreso/gasto (suposición
  marcada en la spec); en el carrito móvil las etiquetas largas empujan los campos de cantidad.
