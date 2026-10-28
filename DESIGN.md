---
name: MyPrueba
description: El mostrador de una tienda de barrio en una sola hoja — lo que entró, lo que debe haber en el cajón y a quién hay que cobrarle, sin que nada distraiga de vender.
colors:
  ground: "#eef3ef"
  surface: "#ffffff"
  raised: "#f4f7f5"
  active: "#dfe8e1"
  line: "#e3e8e4"
  rule: "#eff2f0"
  control: "#c9d1cb"
  ink: "#131416"
  muted: "#55605a"
  accent: "#1b6c80"
  positive: "#1e7b4f"
  negative: "#c4242a"
  warning: "#92400e"
typography:
  text: { fontFamily: "IBM Plex Sans", fontSize: "14px", lineHeight: 1.43, fontWeight: 400 }
  title: { fontFamily: "IBM Plex Sans", fontSize: "22px", fontWeight: 600 }
  lead-figure: { fontFamily: "IBM Plex Sans", fontSize: "28px", fontWeight: 600 }
rounded: { card: "8px", control: "6px", sheet: "16px", pill: "999px" }
spacing: { gap: "20px", card-pad: "20px", canvas: "28px desktop / 16px phone" }
direction: { id: "framed-sheet", kind: "smb-admin", source: "design skill, chosen by the owner as «B · Mostrador» over «Libreta» and «Vitrina»" }
---

## Overview

Modo **Operate**: la gente completa tareas (vender, cobrar, cerrar caja). La dirección
`framed-sheet` se eligió entre tres maquetas (Inicio y Vender a 1440/768/390): un marco verde
grisáceo en L sostiene la navegación y el trabajo va en una hoja blanca con borde fino y esquinas de
16 px. La profundidad sale de un solo paso de tono; no hay sombras en reposo.

Cada pantalla responde una pregunta. Inicio: «¿cómo va el día y qué tengo que atender?» — las ventas
son la cifra más grande, el subtítulo dice el hallazgo del día (el fiado vencido más viejo, o lo que
se acaba) y la acción principal, «Nueva venta», está a un toque. Vender: «¿qué lleva y cuánto paga?»
— el total y el cambio son lo más grande del carrito.

## Colors

- **Marco** `ground` para barra lateral, barra superior y fondo; **hoja** `surface` para el trabajo.
- **Tinta** `ink` para texto y para el único botón lleno por pantalla (casi negro).
- **Acento** `accent` (azul petróleo) solo en datos y selección: las barras de ventas del periodo,
  la cantidad en el carrito, los enlaces, el logo. Menos del 3 % de la pantalla.
- **Estados**: `positive` en ingresos con «+» y cajas abiertas; `negative` en deudas vencidas y
  stock que se acaba; pastillas pálidas (`pill-positive`, `pill-negative`) con tinta del mismo tono.
  Un cero es gris y sin signo.
- **Identidad**: tintes pálidos `tint-a/b/c` para iniciales de personas y productos; nunca son datos.
- Líneas en tres pasos: `line` (bordes de tarjeta), `rule` (filas), `control` (campos y botones).

## Typography

IBM Plex Sans (400, 500, 600), alojada en la app con `@fontsource/ibm-plex-sans`; ninguna petición a
terceros. Escala medida: 12 / 13 / 14 / 15 / 16 / 20 / 22 / 28 px. Título de pantalla 22/600; títulos
de tarjeta 15/600; cifra principal 28/600; cifras hermanas 22/500. Cifras tabulares en toda la app
(`font-variant-numeric: tabular-nums` en `body`). Mayúsculas de frase, sin tracking.

## Layout

- Escritorio (≥ 1024 px): barra lateral de 228 px con grupos «Día a día», «Catálogo», «Personas» y
  «Negocio» abajo; barra superior de 48 px con el estado de la caja; hoja con 28 px de margen
  interior. Inicio en 12 columnas: resumen dividido a todo el ancho; ventas (8) + caja (4);
  movimientos (8) + fiados y «Se está acabando» (4). Vender: catálogo + carrito fijo de 380 px.
- Teléfono y tableta (< 1024 px): barra superior de 56 px con el logo; cinco pestañas (Inicio,
  Vender, Productos, Clientes, Más) y «Más» abre Caja, Inventario, Proveedores y Negocio. Las filas
  de movimientos pasan la hora a la segunda línea. En Vender una barra «Ver carrito» abre el carrito
  a pantalla completa. Objetivos táctiles de 44 px.
- Un solo hueco (20 px) entre bloques, horizontal y vertical.

## Imagery

No hay fotografías: los productos y las personas se reconocen por iniciales sobre tintes pálidos.
Iconos lucide (`lucide-react`), contorno 1,6 px, 16–20 px, grises; nunca dentro de círculos de color.

## Motion

Solo retroalimentación: hover en filas y botones, apertura del menú «Más» y del carrito. Nada entra
animado.

## Components

- `Button`: `primary` casi negro, `secondary` blanco con borde `control`, `danger` rojo para
  confirmar un borrado; tamaño `sm` para acciones dentro de filas.
- Controles segmentados (periodo, tipo de movimiento, forma de pago): pista `raised`, opción activa
  blanca con contorno `line`.
- `.card` (borde `line`, radio 8, sin sombra) y `.pill` (+ `pill-positive|negative|neutral`).
- `BarChart`: una serie, barras cuadradas, 3–5 guías tenues, marcas redondas por encima del máximo,
  sin eje y; el periodo actual en `accent`, el resto en `control`.
- Logo: un toldo de tienda blanco sobre un cuadro `accent` de radio 7 (`BrandMark`).

## Do's and Don'ts

- Sí: el color en datos y estados; un solo botón lleno por pantalla; ceros como ceros.
- No: barras laterales oscuras o saturadas, degradados, sombras en tarjetas, iconos en círculos de
  color, verde/rojo en toda cifra, emojis.
- Aceptado del inspector (`heavy-borders` en Vender): el producto que ya está en el carrito lleva
  borde `control` sobre fondo `raised` para marcar la selección.
- Pendiente de marca: el nombre «MyPrueba» y el logo son provisionales (skill `brand-identity`).
