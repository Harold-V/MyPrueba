---
author: Claude
branch: claude/eager-johnson-chf30t
status: done
---
# Rediseño visual "Mostrador"

## Intent
El dueño ve el frontend "plano y feo". Con la skill `design` se repartieron tres direcciones
(maquetas de Inicio y Vender a 1440/768/390) y eligió **B · Mostrador**: estilo `framed-sheet`
(marco verde grisáceo en L, hoja de trabajo blanca con borde fino y esquinas de 16 px, sin sombras),
IBM Plex Sans, acento azul petróleo `#1b6c80` solo en datos y en una acción. El nombre "MyPrueba"
se mantiene por ahora.

## Scope
- Dentro: tokens de color, tipo y radios; fuentes e iconos alojados en la app (sin CDN); shell
  (barra lateral agrupada con logo de toldo, barra superior con el negocio y el estado de la caja,
  hoja blanca; en celular barra superior y pestañas con "Más"); componentes base; Inicio
  reorganizado (resumen dividido, ventas por período, caja, fiados por cobrar, se está acabando,
  movimientos, hallazgo del día); Vender (tarjetas parejas, carrito con stepper, pago en un control);
  ajuste de color y bordes en el resto de pantallas; `DESIGN.md` con el sistema.
- Fuera: cambios de API o de reglas de negocio, modo oscuro, nombre y logo definitivos de la marca.

## Checklist
- [x] Tokens, fuentes (IBM Plex Sans) e iconos (lucide) — `index.css`, sin peticiones a terceros.
- [x] Shell y componentes base — capturas a 1440 y 390.
- [x] Inicio reorganizado — captura; cifras del día cuadran con la API.
- [x] Vender — captura; cobro con descuento y cambio intacto (pruebas).
- [x] Resto de pantallas sin colores viejos (`emerald`, `slate`, sombras de tarjeta) — grep vacío.
- [x] Inspector de diseño (`inspect.mjs --app`) sin FAIL en Inicio y Vender a 3 anchos.
- [x] `scripts/verify.sh` verde (pruebas ajustadas solo donde cambió la interfaz).
- [x] `DESIGN.md` con el sistema aplicado.

## Evidence
- Direcciones: tres maquetas (Libreta, Mostrador, Vitrina) con los mismos datos, inspector sin FAIL;
  el dueño eligió B · Mostrador. `lucide-react` 1.56.0 y `@fontsource/ibm-plex-sans` 5.3.0
  (versiones de ≥ 2 semanas, fijadas).
- App real con datos sembrados (12 productos, 3 fiados con uno vencido, caja abierta, 5 ventas, abono,
  gasto): capturas de las 9 pantallas a 1440 y 390; sin errores 5xx ni de página; sin scroll
  horizontal a 390.
- Inspector sobre la copia renderizada: Inicio 0 FAIL 0 WARN; Vender 0 FAIL, 1 WARN aceptado
  (borde de selección del producto en el carrito, documentado en `DESIGN.md`).
- Defectos hallados al mirar las capturas y corregidos: la barra superior mostraba la hora de
  apertura de la caja en la zona del navegador (9:39 p. m.) y no en la del negocio (4:39 p. m.); la
  gráfica forzaba el ancho de su tarjeta en el celular (scroll horizontal); la hora se partía en dos
  líneas; «−$0» en la caja; botones grandes en cada fila de productos.
- `scripts/verify.sh`: OK — frontend 95/95, cobertura de líneas 91,2 %, 128,5 KB gzip, 0
  vulnerabilidades.
- Revisión fresca por un subagente (paso 3 de la skill `design`): no se ejecutó; la revisión fue
  propia sobre las capturas.

## Next
Pendientes de UX ya anotados: en el celular, Productos muestra el formulario antes de la lista;
nombre y logo definitivos de la marca (`brand-identity`).
