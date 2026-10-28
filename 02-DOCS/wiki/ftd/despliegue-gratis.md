---
author: Claude
branch: claude/eager-johnson-chf30t
status: in-progress
---
# Hosting gratis con autodeploy

## Intent
Tener la app completa (frontend + backend + SQLite) en internet, gratis, para un usuario, y que
cada merge a `main` se publique solo. GitHub Pages no sirve: solo aloja archivos estáticos.

## Scope
- Dentro: imagen Docker única, Spring sirviendo el frontend, stack con Caddy (HTTPS), workflow
  de CI y despliegue, copias de la base, guía paso a paso.
- Fuera: crear la cuenta de Oracle y la VM (lo hace el dueño), Postgres, dominio propio de pago.

## Checklist
- [x] Spring sirve el frontend con rutas del cliente y `/api` sigue protegido — `SpaFallbackTest` 5/5.
- [x] Imagen de ejecución arranca con SQLite en volumen y contenedor endurecido — compose `healthy`.
- [x] HTTPS de punta a punta con Caddy: `/`, `/vender`, comprobante 200; `/api/auth/me` 401;
      registro 201 con cookie `Secure; SameSite=Strict`; Chromium carga «Crea tu cuenta» sin errores.
- [x] `backup.sh` en caliente: 16 corridas → 14 copias, la copia se lee (`Prueba deploy`).
- [x] Workflow válido — `actionlint` y `shellcheck` limpios; acciones fijadas por SHA.
- [ ] CI verde en GitHub para este PR.
- [ ] Primer despliegue real — requiere la VM y los secretos del paso 5 de la guía.

## Evidence
- Guía y decisión: [stack/deployment.md](../stack/deployment.md).
- La compilación completa dentro de Docker no se pudo correr aquí: el proxy de esta sesión
  intercepta TLS y Maven dentro del contenedor no confía en él. Se probó la etapa final con el
  jar compilado fuera; en GitHub Actions no existe ese proxy.

## Next
El dueño crea la VM y carga los secretos; después, merge a `main`.
