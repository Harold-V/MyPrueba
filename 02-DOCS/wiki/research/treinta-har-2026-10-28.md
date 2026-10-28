---
type: research
title: Treinta — evidencia REA de la app web (HAR 2026-10-28)
tags: [research, rea, treinta]
topic: research
---

# Treinta — evidencia REA de la app web

> Fuente: grabación HAR de la cuenta de prueba del usuario (plan gratuito, sin datos reales) en
> `web.treinta.co`, app web **v5.11.0**, 2026-10-28. 503 peticiones; 156 scripts de Treinta.
> Herramientas: REA 6.8.0 `inspect-web-network-capture` (evidencia `ev_7e29bf6c…`, 503/503 registros,
> encabezados y cookies de autenticación excluidos) y `analyze-javascript-application` (cobertura
> **parcial**: límites de tiempo/memoria en el bundle minificado), más lectura dirigida del HAR.
> **Clean-room (principio 18):** aquí solo hay comportamiento observado — nombres de campos, listas,
> reglas. Ningún código, texto de marca ni recurso de Treinta entra al producto.
> El HAR no se versiona (contiene tokens de sesión).

## Lo que cubre la grabación

Recorrido corto: nueva venta, un **gasto** registrado ($1.000, servicios públicos), creación de una
categoría de producto, edición masiva de un producto (precio, costo, stock), balance e inventario.
**No cubre**: venta con productos, venta fiada, clientes, abonos, proveedores, cierre de caja.

## Hallazgos (verificados)

### Plataforma
- App web en `web.treinta.co`; login en `login.treinta.co` (v1.4.0) con Apple, Google o celular;
  el código llega **por WhatsApp**. Protegido con Cloudflare Turnstile y reCAPTCHA Enterprise.
- APIs: `core-api-gateway.treinta.co`, `web-api-gateway.treinta.co`, `api.prod.treinta.co`;
  websocket propio. Analítica y flags de terceros (Amplitude, Braze, Split, Datadog).

### Transacciones (ventas y gastos)
- **Una sola entidad "transacción"** con `transactionTypeId`: 1 venta, 2 gasto (además: pedido,
  cotización, diferencia de caja ingreso/egreso, etc.).
- `transactionStatusId`: 1 pagada, 2 **a crédito** (fiado), 3 en proceso, 6 fallida.
  **El crédito es un estado, no un método de pago**, y aplica también a **gastos** (deudas con
  proveedores; "No puedes cambiar este gasto en deuda por pagada").
- Fiado con **fecha de vencimiento** (`paymentDueDate`) y tipo de pago a crédito.
- Campos de una transacción: valor, fecha, descripción (si se deja vacía se genera
  "Gasto ddmmyyHHMMSS"), notas, método de pago, categoría de gasto, contacto (cliente/proveedor),
  descuentos, impuestos, propina, domicilio; totales con subtotal, impuestos, descuento y utilidad.
- **Métodos de pago (Colombia):** Efectivo, Tarjeta, Transferencia bancaria, Otro, Link de pago,
  **Nequi**, **Daviplata**; internamente también "deuda" y **pago dividido**.
- **Categorías de gasto (10):** Servicios públicos; Compra de productos e insumos; Arriendo; Nómina;
  Gastos administrativos; Mercadeo y publicidad; Transporte, domicilios y logística; Mantenimiento y
  reparaciones; Muebles, equipos o maquinaria; Otros.

### Balance
- `GET /balance/{store}?startDate&endDate` → `{sales, spendings, utility}` (rango en epoch ms,
  zona America/Bogota).
- Texto de la app: los egresos "incluyen tus gastos pagados y abonos que realices a deudas" →
  **balance de caja** (coincide con nuestra decisión).
- Existe **caja** ("money-box", cierre de caja con diferencias) y pantalla `/balance/cash-box`.

### Productos e inventario
- Producto: nombre, precio, **costo**, **stock**, **stock mínimo**, estado de stock (p. ej. HIGH),
  **categoría** (categorías creadas por el negocio), SKU, unidad de medida, **impuestos**
  (IVA 19 %, IVA 5 %, impoconsumo 8 %, exento, ninguno), notas, imagen, variantes, productos
  compuestos (recetas/insumos).
- Ajuste de **stock negativo configurable** por negocio (`stockBehavior: ALLOW_NEGATIVE` por defecto)
  y alertas de stock bajo activables.
- Resumen de inventario: valor del inventario, número de referencias, precio promedio; contador de
  productos en stock bajo.
- Motivos al ajustar stock por edición de venta: devolución del cliente, corregir un error, cambio
  de producto, otro.
- Edición masiva de productos y carga desde Excel; filtros por stock, ventas 30 días, nombre, fecha,
  precio.

### Negocio
- ~30 **tipos de negocio** agrupados (retail, restaurantes y licores, servicios): tienda de barrio,
  minimercado, ferretería, farmacia, ropa y calzado, restaurante, etc.

### Mapa de pantallas (rutas de la app, para planear módulos)
Balance (venta, confirmación, caja), inventario (crear, carga masiva, IA, fotos, edición masiva,
compuestos/insumos), registrar compra, deudas, clientes, proveedores, estadísticas (resumen,
ganancias, ventas, productos, clientes, empleados, gastos), empleados y horarios, facturación
electrónica, cotizaciones, tienda virtual/ecommerce y pedidos, domicilios, mesas (restaurante),
menú digital, planes/suscripción, configuración, notificaciones, ayuda.

## Comparación con lo construido

| Tema | MyPrueba hoy | Treinta (evidencia) | Estado |
|---|---|---|---|
| Ventas y gastos | Entidades separadas | Una transacción con tipo | Diferente por dentro, igual para el usuario |
| Balance de caja (fiado no es ingreso) | Sí | Sí | ✅ Coincide |
| Fiado | Estado a crédito con vencimiento (fase 1) | **Estado** a crédito, con fecha de vencimiento | ✅ Alineado |
| Deudas con proveedores | Diferido | Sí (gastos a crédito) | ❌ Falta |
| Métodos de pago | + Nequi, Daviplata, Link de pago (fase 1) | + pago dividido | ✅ Salvo pago dividido |
| Categorías de gasto | Las 10 de Treinta (fase 1) | 10 | ✅ Alineado |
| Tipos de negocio | Los 59 en 3 grupos (fase 1) | 59 agrupados | ✅ Alineado |
| Descripción vacía en gasto | "Gasto ddMMaaHHmmss" (fase 1) | Igual | ✅ (zona horaria a confirmar) |
| Producto: costo | Opcional | Sí | ✅ |
| Stock | Todo producto con stock y mínimo (fase 1) | Igual | ✅ Alineado |
| Stock negativo | Configurable, permitido por defecto (fase 1) | Igual | ✅ Alineado |
| Categorías de producto, SKU, unidad, impuestos | No | Sí | ❌ Faltan |
| Motivos de ajuste de stock | Entrada, pérdida, conteo | + motivos al editar venta | ⚠️ Parcial |
| Resumen de inventario | Valor, stock bajo | Valor, referencias, precio promedio, stock bajo | ✅ Casi |
| Login | Email + contraseña | Celular + código por WhatsApp, Google, Apple | ⚠️ Diferente (decisión del usuario) |

## Pendiente de evidencia

Venta con productos, fiado y abonos de clientes, proveedores y compras, cierre de caja, estadísticas.
Para cubrirlos hace falta otra grabación HAR recorriendo esas pantallas.
