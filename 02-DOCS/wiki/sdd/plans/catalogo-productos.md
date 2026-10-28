---
type: plan
title: Plan — Catálogo de productos
tags: [sdd, plan]
topic: sdd
slug: catalogo-productos
status: approved in autopilot
---

# Plan — Catálogo de productos

## Módulos
- **catalog**: categorías, código de barras, grupos de variantes, búsqueda y carga masiva.
  `ProductView` += `categoryId`, `barcode` (el inventario filtra por categoría).
- **inventory**: `GET /api/inventory?categoryId&q` filtra con los datos del catálogo.
- Ventas, compras y ajustes no cambian: una variante es un producto.

## Contratos
```text
/api/product-categories CRUD          Category = {id, name}
Product += categoryId?, categoryName?, barcode?, groupId?, variant?
GET /api/products?categoryId&q        (q: nombre o código, sin distinguir mayúsculas)
POST /api/product-groups  {name, categoryId?, price?, cost?, variants:[{name, price?, cost?, stock?, minStock?, barcode?}]}
  -> {id, name, categoryId, variants:[Product]}   (precio/costo de la variante por defecto los del grupo)
PUT  /api/product-groups/{id} {name, categoryId?}  renombra las variantes
POST /api/product-groups/{id}/variants {name, price, cost?, stock?, minStock?, barcode?}
DELETE /api/product-groups/{id}                    elimina las variantes
PUT /api/products/{id}: en una variante, `variant` reemplaza a `name`
POST /api/products/import (multipart file) -> {created}
  400 validation_failed con errors[{field:"rows[3].price", code}] (fila del archivo, 1 = encabezado)
```

## Datos (V8)
`product_categories`; `product_groups`; `products` + category_id, barcode, group_id, variant;
índice único (business_id, barcode) parcial (`WHERE barcode IS NOT NULL`, portable a PostgreSQL).

## Excel
`org.dhatim:fastexcel-reader` (pequeño, sin POI) para `.xlsx`; `.csv` con `;` o `,`.
Encabezados sin tildes ni mayúsculas. Límite 2.000 filas y 2 MB.
