---
type: plan
title: Plan — Cuentas y negocio
description: Plan de estructura para registro, sesión y negocio; define además la arquitectura base compartida.
tags: [sdd, plan]
timestamp: 2026-10-28T06:00:00Z
topic: sdd
slug: cuentas-y-negocio
status: approved in autopilot
---

# Plan — Cuentas y negocio

> Spec: [../specs/cuentas-y-negocio.md](../specs/cuentas-y-negocio.md) · Constitution: [../constitution.md](../constitution.md) v1.1.0 · Status: approved in autopilot
> Last updated: 2026-10-28. Este plan fija también la **arquitectura base** que hereda
> [libro-ventas-gastos-balance](libro-ventas-gastos-balance.md).

## 0. Global Constraints

- **Backend:** Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2, Maven wrapper. Paquete raíz `com.myprueba`.
- **Frontend:** React 19 + Vite + Tailwind CSS 4 + TypeScript `strict`, Node 24, npm.
- **BD:** SQLite vía `sqlite-jdbc`; esquema solo por Flyway (`backend/src/main/resources/db/migration`),
  SQL portable: `VARCHAR`, `BIGINT`, `TIMESTAMP`, sin funciones propias de SQLite. Acceso con
  `JdbcClient` (sin JPA: evita depender de un dialecto comunitario de Hibernate para SQLite).
- **IDs:** UUID v4 en `VARCHAR(36)`.
- **Dinero (principio 23):** BD `BIGINT` centavos; Java `BigDecimal` escala 2; JSON string decimal
  `"15000.00"`. Cantidades: BD `BIGINT` milésimas; JSON string `"2.5"`.
- **Errores:** RFC 9457 `application/problem+json` con `type`, `title`, `status`, `code` y, en
  validación, `errors: [{ field, code }]`. Los textos visibles los pone el frontend (principio 12).
- **Aislamiento:** toda consulta de datos de negocio filtra por `business_id` del usuario en sesión;
  un recurso ajeno responde **404** idéntico a uno inexistente.
- **Commits:** gitmoji + Conventional Commits; sin trailer de IA.
- **Zona y moneda:** `America/Bogota`, `COP`, fijas en este ciclo.

## 1. Context & constraints

- Criterios de aceptación que dan forma: email único sin mayúsculas, error de login idéntico,
  cierre de sesión efectivo, aislamiento entre negocios.
- Principios en juego: 4 (monorepo + `/api` + OpenAPI), 7-8 (TDD, `verify()`), 16-17 (secretos,
  aislamiento), 19 (móvil 360 px), 23 (dinero).
- Fuera de alcance: SMS, recuperación de contraseña, varios negocios, roles, límite de intentos.

## 2. Architecture

```text
[ React SPA (frontend/) ] --HTTPS JSON /api, cookie sid--> [ Spring Boot app (backend/) ]
                                                               | modules (Spring Modulith)
                                                               |  accounts  -> users, businesses, sessions
                                                               |  catalog   -> products            (plan libro)
                                                               |  ledger    -> sales, expenses     (plan libro)
                                                               |  shared    -> money, errors, clock (OPEN)
                                                               v
                                                        [ SQLite file + Flyway ]
```

- **frontend** (internal) — SPA móvil; en desarrollo Vite hace proxy de `/api` a `:8080`.
- **accounts** (internal) — registro, login, logout, sesión, negocio; expone `CurrentBusiness`.
- **shared** (internal, módulo OPEN) — `Money`, `Quantity`, manejador de Problem Details, `Clock`.
- **SQLite** (external) — archivo `./data/myprueba.db`, WAL, `busy_timeout`, `foreign_keys=on`.

**Decisión principal:** sesión opaca en cookie `HttpOnly; SameSite=Strict; Path=/api` con el hash
SHA-256 del token guardado en BD, en lugar de JWT. Cerrar sesión borra la fila, así el criterio
"tras cerrar sesión se rechaza" se cumple de verdad; un JWT seguiría válido hasta caducar. CSRF:
`SameSite=Strict` + la API solo acepta `application/json` en escrituras (un formulario de otro
sitio no puede enviarlo sin preflight CORS, y CORS no está abierto).

## 3. Interfaces & contracts

```text
POST /api/auth/register {email, password, businessName, businessCategory?}
  -> 201 Me + Set-Cookie sid | 400 validation{errors[field:email|password|businessName|businessCategory]}
  | 409 code=email_taken
POST /api/auth/login {email, password} -> 200 Me + Set-Cookie sid | 401 code=invalid_credentials (idéntico)
POST /api/auth/logout -> 204 + cookie expirada (sin sesión: 204 igual)
GET  /api/me -> 200 Me | 401 code=unauthenticated
PUT  /api/business {name, category?} -> 200 Business | 400 | 401
Me = {user:{id,email}, business:Business}
Business = {id, name, category|null, currency:"COP", timeZone:"America/Bogota"}
category ∈ {GROCERY, FOOD, CLOTHING, BEAUTY, CATALOG_SALES, SERVICES, OTHER}

accounts API (para otros módulos):
  CurrentBusiness.id() -> UUID              (solo dentro de petición autenticada)
  CurrentBusiness.zone() -> ZoneId
```

- Email: `trim` + minúsculas; formato `x@y.z` básico; ≤ 254 chars.
- Contraseña: 8..72 bytes UTF-8 (límite de BCrypt); hash BCrypt.
- Login con email inexistente ejecuta igualmente una comparación BCrypt ficticia (tiempo similar).
- Sesión: token aleatorio de 32 bytes (base64url); caduca a los 7 días sin uso; se extiende como
  mucho una vez por hora para no escribir en cada petición.

## 4. Data model & flow

- **users** — id, email (UNIQUE), password_hash, created_at.
- **businesses** — id, owner_user_id (UNIQUE → users), name, category, currency, time_zone, created_at, updated_at.
- **sessions** — token_hash (PK), user_id → users, expires_at, last_seen_at, created_at.

Flujo registro: validar → transacción {insert user, insert business, insert session} → cookie → `Me`.
Email duplicado: se detecta por consulta previa y, en carrera, por la restricción UNIQUE → 409.

## 5. Testing strategy

| Criterio | Nivel | Afirma | Falsos |
| --- | --- | --- | --- |
| registro ok / duplicado / contraseña 7 / nombre vacío | integración (MockMvc + SQLite temporal) | status, `code`, cookie | nada |
| login ok / error idéntico | integración | cuerpos 401 byte a byte iguales | nada |
| logout invalida | integración | `/api/me` → 401 tras logout con la misma cookie | nada |
| aislamiento A/B | integración | A no ve ni cambia datos de B | nada |
| contraseña no en claro | integración | columna ≠ contraseña, prefijo BCrypt | nada |
| sesión caducada | unit | `expires_at` pasado → no autentica | `Clock` fijo |
| límites de módulos | unit | `ApplicationModules.verify()` | — |
| pantallas registro/login | componente (Vitest + Testing Library) | errores por campo, redirección | `fetch` |

SQLite real en cada test de integración (archivo temporal): los mocks de BD no prueban migraciones.

## 6. Sequencing & dependencies

1. Andamiaje backend (pom, wrapper, Spotless, JaCoCo, Modulith verify) — serial.
2. Andamiaje frontend (Vite, Tailwind, ESLint, Prettier, Vitest) — paralelizable con 1.
3. `shared` (Money, Quantity, Problem Details) — depende de 1.
4. Migración V1 + `accounts` (registro, login, logout, me, negocio, filtro de seguridad) — depende de 3.
5. Frontend auth (cliente API, registro, login, guardas de ruta, negocio) — depende de 2 y del contrato §3.

## Tasks
<!-- generated by tasks on 2026-10-28; IDs are stable, do not renumber -->

| ID | [P] | Task | Done-check | Depends-on | Trace |
| --- | --- | --- | --- | --- | --- |
| T001 |  | Andamiaje backend: pom Boot 4.1.2 + Modulith, wrapper, Spotless, JaCoCo ≥ 80 %, app arranca | `./mvnw verify` verde con test de contexto y `verify()` | — | const. 1, 5, 7, 8 |
| T002 | [P] | Andamiaje frontend: Vite React TS strict, Tailwind 4, ESLint, Prettier, Vitest | `npm run lint && npm run typecheck && npm test && npm run build` verdes | — | const. 2, 5, 6 |
| T003 |  | `shared`: Money/Quantity (parse, format, cents) y manejador Problem Details | tests unit de Money/Quantity rojos→verdes | T001 | const. 11, 23 |
| T004 |  | Migración V1 users/businesses/sessions | Flyway aplica en SQLite temporal | T001 | spec Behaviour |
| T005 |  | Registro (tests primero) | criterios registro verdes | T003, T004 | spec AC 1-4 |
| T006 |  | Login, logout, me, filtro de sesión (tests primero) | criterios login/logout/401 verdes | T005 | spec AC 5-7, 10, 11 |
| T007 |  | Editar negocio + test de aislamiento A/B | criterios negocio y A/B verdes | T006 | spec AC 8-9 |
| T008 |  | Frontend: cliente API, contexto de sesión, rutas protegidas | tests de componente verdes | T002, T006 | spec Main path |
| T009 |  | Frontend: pantallas registro, login, negocio, cerrar sesión | tests de componente verdes | T008 | spec AC 1-9 |

**Forecast:** ~2.5k líneas entre backend y frontend con sus tests; se revisa junto con el plan del
libro en un único PR por ser la primera entrega. Riesgo principal: compatibilidad de librerías con
Boot 4.1 / Java 25 (mitigado verificando cada versión al construir).
