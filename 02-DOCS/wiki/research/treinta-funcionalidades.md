---
type: research
title: Treinta — catálogo de funcionalidades públicas y brecha con MyPrueba
tags: [research, treinta, backlog]
topic: research
---

# Treinta — funcionalidades documentadas y brecha

> Fuente: páginas públicas de treinta.co (2026-10-28), indicadas por el usuario como el alcance a
> replicar: [inventarios](https://www.treinta.co/software-de-control-de-inventarios-para-tu-negocio/),
> [ventas y gastos](https://www.treinta.co/registro-ventas-gastos/),
> [reportes](https://www.treinta.co/app-para-reportes-de-ventas-y-estadisticas-de-tu-negocio/),
> [proveedores](https://www.treinta.co/programa-de-gestion-de-proveedores-para-tu-negocio/),
> [clientes](https://www.treinta.co/programa-para-manejar-base-de-datos-de-clientes/),
> [deudas](https://www.treinta.co/control-de-deudas/). Complementa la evidencia REA en
> [treinta-har-2026-10-28](treinta-har-2026-10-28.md). Solo comportamiento (principio 18).

Leyenda: ✅ hecho · ⚠️ parcial o distinto · ❌ falta.

## Registro de ventas y gastos
| Funcionalidad | Estado |
|---|---|
| Registro rápido de ventas (celular o computador) | ✅ |
| Registro de gastos del día a día | ✅ (categorías distintas a las de Treinta ⚠️) |
| Flujo de caja en tiempo real (entra, sale, disponible) | ✅ balance de caja |
| Reportes por día, semana o mes | ✅ consulta · ❌ descarga |

## Control de inventarios
| Funcionalidad | Estado |
|---|---|
| Stock baja automáticamente con cada venta | ✅ (todo producto con stock desde la fase 1) |
| Vista del stock en tiempo real, alertas de stock bajo | ✅ |
| Stock sube al registrar una **compra de mercancía** como gasto | ❌ |
| **Categorías** de productos | ❌ |
| **Variantes** (talla, color, presentación) con stock propio | ❌ |
| **Carga masiva desde Excel** (nombre, código de barras, costo, precio, categoría) | ❌ |

## Reportes y estadísticas
| Funcionalidad | Estado |
|---|---|
| Resumen por período: ingresos y **número de transacciones** | ⚠️ falta el número |
| **Comparativa** con el periodo anterior | ❌ |
| **Productos más vendidos** | ❌ |
| **Rendimiento por empleado** | ❌ (requiere empleados) |
| **Descarga** de reportes de ventas e inventario | ❌ |

## Proveedores
| Funcionalidad | Estado |
|---|---|
| Ficha del proveedor (nombre, teléfono, contacto) | ❌ |
| Historial de compras por proveedor | ❌ |
| Deudas con proveedores y **cuentas por pagar** con abonos | ❌ |
| Compras vinculadas al inventario y a los gastos | ❌ |

## Clientes
| Funcionalidad | Estado |
|---|---|
| Ficha: nombre, teléfono, **documento**, **dirección** | ✅ (fase 2) |
| **Historial de compras** por cliente (todas sus ventas) | ✅ (fase 2) |
| Deudas y abonos, "cuánto y **desde cuándo**" | ✅ (antigüedad y vencidas en fase 1) |
| Datos del cliente en **comprobantes** | ❌ (no hay comprobantes) |
| **Crear cliente desde la venta** sin salir del flujo | ✅ (fase 2) |

## Deudas
| Funcionalidad | Estado |
|---|---|
| Deudas por cliente con historial | ✅ |
| Abonos parciales con saldo automático | ✅ |
| Resumen de cuentas por cobrar y deudores activos | ✅ |
| Deudas con proveedores | ❌ |

## Funcionalidades transversales (presentes en todas las páginas)
Variantes de productos · catálogo virtual compartible · descuentos en ventas · gestión de empleados
(horarios, funciones, ventas) · reportes personalizados de flujo de caja · comprobantes digitales.
Todas ❌.
