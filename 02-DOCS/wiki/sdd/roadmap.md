---
type: roadmap
title: Hoja de ruta — réplica funcional de Treinta
tags: [sdd, roadmap]
topic: sdd
---

# Hoja de ruta

> Alcance: las funcionalidades públicas de Treinta ([catálogo y brecha](../research/treinta-funcionalidades.md))
> más la evidencia REA ([HAR](../research/treinta-har-2026-10-28.md)). Cada fase es una spec SDD.
> El orden sigue las dependencias: cada fase usa lo que dejan las anteriores.

| Fase | Spec | Contenido | Depende de |
|---|---|---|---|
| ✅ 0 | cuentas, libro, inventario, deudas | Núcleo ya construido | — |
| ✅ 1 | `alineacion-treinta` | Métodos de pago CO (Nequi, Daviplata, link de pago); 10 categorías de gasto; ~30 tipos de negocio; descripción automática; fiado como **estado** con fecha de vencimiento; stock en todos los productos con política de stock negativo | 0 |
| ✅ 2 | `clientes-completo` | Documento y dirección; historial de **todas** las compras; antigüedad de la deuda; crear cliente desde la venta | 1 |
| ✅ 3 | `proveedores-compras` | Proveedores; **compra de mercancía** (gasto con productos que sube el stock); compras y gastos a crédito; **cuentas por pagar** con abonos; historial por proveedor | 1, 2 |
| ✅ 4 | `catalogo-productos` | Categorías; código de barras/SKU; **variantes** con stock propio; **carga masiva desde Excel** | 1 |
| 5 | `reportes-estadisticas` | Número de transacciones; comparativa con periodo anterior; más vendidos; descarga de ventas, gastos e inventario | 3, 4 |
| ✅ 6 | `sistema-ventas` (antes `ventas-avanzadas`) | **Punto de venta**; descuentos; **comprobantes digitales** numerados; **caja** con apertura y cierre | 2, 4 |
| 7 | `empleados` | Empleados, permisos y ventas por empleado (habilita rendimiento por empleado) | 5 |
| 8 | `catalogo-virtual` | Catálogo público compartible con fotos y precios | 4 |

Fuera de esta hoja de ruta (aparecen en la app, no en las páginas indicadas): facturación
electrónica DIAN, mesas y menú de restaurante, tienda virtual con pedidos, domicilios, planes de pago.
