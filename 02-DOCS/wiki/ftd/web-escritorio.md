---
author: Claude
branch: claude/eager-johnson-chf30t
status: in-progress
---
# Diseño web de escritorio

## Intent
La app es web ([plataforma](../product/plataforma.md)). Hoy en escritorio se ve como una columna de
celular. Debe aprovechar el ancho sin dejar de funcionar a 360 px.

## Scope
- Dentro: layout con menú lateral en pantallas ≥ 1024 px y barra inferior en celular; contenido
  amplio en inicio y productos; formularios con ancho de lectura cómodo.
- Fuera: cambios de comportamiento, nuevas pantallas, modo oscuro.

## Checklist
- [x] Layout responsive (menú lateral ↔ barra inferior) — captura a 1280 px y 360 px.
- [x] Inicio en escritorio: resumen y acciones en fila, movimientos a todo el ancho — captura.
- [x] Productos en escritorio: formulario y lista en dos columnas — captura.
- [x] Sin scroll horizontal a 360 px y pruebas existentes en verde — Playwright + vitest.

## Evidence
- Layout: Playwright a 1280 px muestra menú lateral (Inicio/Productos/Negocio); a 360 px, barra inferior.
- Inicio 1280 px: resumen ($15.000 / $5.000 / $10.000) y botones en una fila; movimientos a todo el ancho.
- Productos 1280 px: formulario (izquierda, fijo) y lista (derecha) en dos columnas.
- `horizontalScroll360: false`; `vitest`: 37/37 verdes; lint y `tsc` limpios.

## Next
Hecho. Siguiente trabajo: spec de inventario (SDD).
