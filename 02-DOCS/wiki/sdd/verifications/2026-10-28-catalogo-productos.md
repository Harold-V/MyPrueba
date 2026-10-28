---
type: verification
title: Verify — catálogo de productos (fase 4)
tags: [sdd, verify]
topic: sdd
---

# Verify — catálogo de productos (fase 4)

> Fecha: 2026-10-28 · Veredicto: **PASS**

- Gate `scripts/verify.sh` exit 0: backend 139/139 (12 nuevas en `CatalogApiTest`,
  `ProductGroupApiTest`, `ProductImportApiTest`), frontend 81/81 (10 nuevas: `catalog.test.tsx`,
  `search.test.ts`), cobertura frontend 92,8 % de líneas, 116,2 KB gzip, 0 vulnerabilidades.
  Dependencia nueva: `org.dhatim:fastexcel-reader` 0.20.2 (y `fastexcel` solo en pruebas).
- Criterios (con prueba): filtro por categoría y producto sin categoría al borrarla; código de barras
  único y buscable; variantes M/L con stock propio (vender M no toca L); renombrar el grupo renombra
  las variantes y borrarlo las borra; Excel de 3 filas crea productos, stock y categoría; precio
  inválido en la fila 3 → nada creado y error `rows[3].price`; aislamiento A/B.
- Seguridad: la carga masiva recibe el archivo como cuerpo crudo (`.xlsx` o `text/csv`), no
  multipart, para que siga necesitando preflight CORS como las escrituras JSON (defensa CSRF).
  Multipart → 415.
- Migración: V8 aplicada sobre la base de la fase 3 sin errores.
- Navegador (Chromium, 1280 px y 360 px): `.xlsx` real (openpyxl) con 3 filas → "3 productos
  cargados" y categorías Bebidas, Aseo, Granos; producto con categoría y código; "Camiseta" con
  variantes M y L; búsqueda "jabon" encuentra "Jabón azul"; en la venta, escanear 7702004003508 +
  Enter agrega la gaseosa y se suma "Camiseta — M" (total $28.000); inventario M 2, L 2; filtro
  Bebidas muestra solo sus 2 productos. Sin errores 5xx ni scroll horizontal.
- Defecto encontrado y corregido en el recorrido: el formulario de producto conservaba la
  categoría anterior tras crear uno; ahora se limpia (prueba añadida).
- Pendiente de UX (no bloquea): en móvil la lista de productos queda debajo de los tres paneles
  (nuevo, categorías, carga); conviene subir la búsqueda y la lista.
