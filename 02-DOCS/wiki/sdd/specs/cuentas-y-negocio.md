---
type: spec
title: Spec — Cuentas y negocio
description: WHAT and WHY para registro, inicio de sesión y el negocio del dueño — base de todos los módulos.
tags: [sdd, spec]
timestamp: 2026-10-28T05:30:00Z
topic: sdd
slug: cuentas-y-negocio
status: approved in autopilot
---

# Spec — Cuentas y negocio

> Slug: `cuentas-y-negocio` · Status: approved in autopilot (2026-10-28) · Created: 2026-10-28
> Inherits: [constitution](../constitution.md) v1.0.0
> Referente: Treinta (réplica funcional clean-room). Evidencia REA: ninguna aún — todo comportamiento
> atribuido a Treinta está `sin verificar` (principio 22).

## Problem & why
El dueño de un micronegocio necesita que sus ventas, gastos y productos sean suyos: que solo él los
vea, que sigan ahí cuando cambia de celular y que nadie más los toque. Sin una cuenta y un negocio al
que pertenezcan los datos, ningún otro módulo (libro, inventario, deudas, reportes) puede existir.

## Cost of not building it
No hay producto. Todo módulo posterior depende de saber quién es el usuario y a qué negocio
pertenece cada registro; sin esto, los datos financieros de un negocio quedarían expuestos a otros
(viola el principio 17).

## The cheapest alternative
Una app sin cuentas que guarde todo solo en el dispositivo. Elimina el registro, pero los datos se
pierden al cambiar de celular o borrar el navegador, no hay acceso desde otro dispositivo y bloquea
cualquier módulo futuro que necesite servidor (catálogo, empleados). No basta.

## Goals
- Un dueño crea su cuenta y su negocio en un solo paso, en menos de un minuto.
- El dueño entra desde cualquier dispositivo con su email y contraseña y encuentra sus datos.
- Ningún usuario puede ver ni modificar datos de un negocio que no es el suyo.

## Non-goals / out of scope
- Inicio de sesión por celular + código SMS (Treinta lo usa — `sin verificar`). Diferido.
- Recuperación de contraseña por email. Diferido (necesita proveedor de correo).
- Varios negocios por dueño, empleados, roles y permisos. Diferidos.
- Inicio de sesión con Google/Apple. Diferido.
- Borrar la cuenta y exportar datos. Diferido.
- Verificación del email. Diferida.

## Users & context
El dueño de un micronegocio en Colombia (tienda de barrio, peluquería, venta por catálogo, comidas)
que usa el celular como herramienta principal y no es técnico. Quiere empezar a anotar ventas de
inmediato, sin formularios largos.

## Behaviour
- Main path — registro: el dueño indica email, contraseña, nombre del negocio y, opcionalmente, el
  tipo de negocio de una lista. Queda con sesión iniciada y ve su negocio vacío listo para usar.
- Main path — inicio de sesión: con email y contraseña correctos entra a su negocio.
- Main path — cerrar sesión: tras cerrarla, nada del negocio es accesible sin volver a entrar.
- Main path — editar negocio: el dueño cambia el nombre y el tipo de su negocio.
- Edge: el email se trata sin distinguir mayúsculas/minúsculas ni espacios al inicio o final.
- Edge: un email ya registrado no puede registrarse otra vez.
- Edge: la contraseña tiene al menos 8 caracteres.
- Edge: el nombre del negocio es obligatorio, de 1 a 80 caracteres tras quitar espacios.
- Edge: la sesión caduca tras un tiempo de inactividad y el dueño debe volver a entrar.
- Error: con email o contraseña incorrectos se muestra el mismo mensaje, sin revelar si el email
  existe.
- Error: datos inválidos en el registro muestran qué campo falla y por qué, sin perder lo escrito.
- Error: cualquier intento de acceder a datos sin sesión válida se rechaza.

## Acceptance criteria
- Given un email no registrado, When el visitante se registra con email, contraseña de ≥ 8
  caracteres y nombre de negocio, Then queda con sesión iniciada y ve su negocio con ese nombre.
- Given un email ya registrado (en cualquier combinación de mayúsculas), When alguien intenta
  registrarse con él, Then el registro se rechaza indicando que el email ya está en uso.
- Given una contraseña de 7 caracteres, When se intenta registrar, Then se rechaza indicando el
  mínimo de 8.
- Given un nombre de negocio vacío o de solo espacios, When se intenta registrar, Then se rechaza.
- Given un dueño registrado, When inicia sesión con sus credenciales, Then accede a su negocio.
- Given un email inexistente o una contraseña errónea, When se intenta iniciar sesión, Then el
  mensaje de error es idéntico en ambos casos.
- Given un dueño con sesión, When cierra sesión, Then las consultas a sus datos se rechazan hasta
  volver a entrar.
- Given dos dueños A y B, When A intenta leer o modificar cualquier dato del negocio de B, Then se
  rechaza y los datos de B no cambian.
- Given un dueño con sesión, When cambia el nombre de su negocio, Then el nuevo nombre se muestra en
  adelante.
- Given una petición sin sesión válida, When pide datos de un negocio, Then se rechaza.
- La contraseña nunca se guarda ni se muestra en claro.

## Points to clarify
- **pregunta abierta** — Nombre de la marca propia que verá el usuario. Hasta decidirlo se usa el
  nombre de trabajo "MyPrueba".
- **suposición tomada** — La sesión caduca tras 7 días sin uso. *Base:* uso diario en el celular;
  volver a entrar cada día sería fricción. *Riesgo:* si se pide más seguridad, baja el plazo; no
  cambia ningún otro criterio.
- **suposición tomada** — La lista de tipos de negocio es fija: tienda/abarrotes, restaurante/comidas,
  ropa/calzado, belleza/peluquería, venta por catálogo, servicios, otro. *Base:* rubros típicos de
  micronegocio en Colombia (inspirados en el referente, `sin verificar`). *Riesgo:* bajo; solo
  etiqueta.
- **suposición tomada** — Moneda del negocio COP y zona horaria America/Bogota, fijas en este ciclo.
  *Base:* principio 10 y mercado inicial. *Riesgo:* otros países requieren elegir moneda/zona.
- **decisión diferida** — Límite de intentos de inicio de sesión (fuerza bruta). Fuera de este ciclo;
  riesgo aceptado mientras no haya despliegue público.
- **área no formulable** — Cómo migrará una cuenta email+contraseña al login por celular cuando
  llegue.
