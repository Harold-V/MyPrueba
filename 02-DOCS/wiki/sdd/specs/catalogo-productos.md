---
type: spec
title: Spec — Catálogo de productos (fase 4)
description: Categorías, código de barras, variantes con stock propio, búsqueda al vender y carga masiva desde Excel.
tags: [sdd, spec]
timestamp: 2026-10-28T21:00:00Z
topic: sdd
slug: catalogo-productos
status: approved in autopilot
---

# Spec — Catálogo de productos (fase 4)

> Slug: `catalogo-productos` · Status: approved in autopilot (2026-10-28) · Inherits: constitution v1.1.0
> Fuentes: [página de inventarios](https://www.treinta.co/software-de-control-de-inventarios-para-tu-negocio/)
> (✔ = descrito ahí) y [HAR](../../research/treinta-har-2026-10-28.md) (★ = observado: categorías
> creadas por el negocio, SKU, carga masiva y edición masiva en el mapa de pantallas).

## Problem & why
Con muchos productos el dueño no los encuentra al vender, no puede ordenarlos, no puede vender ropa
por talla o color con stock separado y tiene que crear uno por uno lo que ya tiene en Excel.

## Cost of not building it
Ventas lentas en mostrador, stock mezclado entre tallas y horas de digitación al empezar.

## The cheapest alternative
Nombres largos ("Camiseta M roja") y crear todo a mano. Sirve con pocos productos; no escala y no
permite buscar por código.

## Goals
- Categorías creadas por el negocio y asignadas a productos ✔★.
- Código de barras / SKU por producto, único en el negocio ✔★.
- Productos con variantes (talla, color, presentación), cada una con precio, costo y **stock propio**
  que se descuenta por separado al vender ✔.
- Buscar productos por nombre o código y filtrar por categoría al vender y en el inventario ✔.
- Carga masiva desde Excel con nombre, código de barras, costo, precio y categoría ✔.

## Non-goals / out of scope
- Impuestos por producto, unidad de medida, imágenes, productos compuestos (recetas).
- Edición masiva de precios, exportar a Excel (va con reportes, fase 5).
- Lector de códigos con cámara (el lector USB escribe el código como teclado y basta).

## Users & context
El dueño al montar su catálogo y al vender en el mostrador.

## Behaviour
- Categorías: crear, renombrar y eliminar (sus productos quedan sin categoría). Nombre único.
- Producto: nombre, precio, costo, categoría, código de barras opcional (único en el negocio).
- Producto con variantes: un nombre común (p. ej. "Camiseta"), categoría y una lista de variantes
  (p. ej. "M / Roja") con precio, costo, stock inicial, stock mínimo y código propios. Cada variante
  se vende, se compra y se ajusta como un producto ("Camiseta — M / Roja"). Se pueden agregar
  variantes después, renombrar el producto común (renombra todas) y eliminarlo (elimina todas).
- Lista de productos e inventario filtrables por categoría y por texto (nombre o código).
- Al vender: buscar por nombre o código; escribir un código exacto y pulsar Enter agrega el producto.
- Carga masiva: archivo `.xlsx` o `.csv` con encabezados Nombre, Código de barras, Costo, Precio,
  Categoría y, opcionales, Stock y Stock mínimo. Las categorías que no existan se crean. Todo o nada:
  si una fila tiene un error, no se crea ninguna y se informa fila y columna. Máximo 2.000 filas.
- La app ofrece una plantilla para descargar.

## Acceptance criteria
- Given la categoría "Bebidas" y un producto con ella, When se filtra por "Bebidas", Then solo
  aparece ese producto; When se elimina la categoría, Then el producto queda sin categoría.
- Given un producto con código "7702004003508", When otro producto usa el mismo código, Then se
  rechaza; When se busca "7702004", Then aparece.
- Given "Camiseta" con variantes "M" (stock 3) y "L" (stock 2), When se vende 1 "Camiseta — M",
  Then M queda en 2 y L sigue en 2.
- Given ese producto, When se renombra a "Polo", Then las variantes se llaman "Polo — M" y "Polo — L";
  When se elimina, Then ninguna variante existe.
- Given un Excel con 3 filas válidas y una categoría nueva, When se carga, Then hay 3 productos con
  su stock y la categoría existe.
- Given un Excel con un precio inválido en la fila 3, When se carga, Then no se crea ningún producto
  y el error dice fila 3, columna Precio.
- Given datos de otro negocio, Then categorías, productos y variantes responden como inexistentes.

## Points to clarify
- **suposición tomada** — Las variantes son productos agrupados (no una tabla aparte de stock).
  *Base:* "cada variante se descuenta por separado"; reutiliza ventas, compras e inventario.
  *Riesgo:* bajo.
- **suposición tomada** — La carga masiva solo crea; un nombre o código repetido es error de fila.
  *Riesgo:* bajo (la edición masiva queda fuera).
