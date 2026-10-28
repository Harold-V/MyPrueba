---
type: decisions
title: MyPrueba — SDD decisions
description: Registro append-only de decisiones significativas (principio 21).
tags: [sdd, decisions]
topic: sdd
---

# Decisiones

| Fecha | Decisión | Opciones consideradas | Por qué |
|-------|----------|-----------------------|---------|
| 2026-10-28 | Constitución v1.0.0 ratificada. | — | Arranque del proyecto. |
| 2026-10-28 | Backend Java 25 LTS + Spring Boot 4 + Spring Modulith 2 + Maven. | Java 21 + Boot 3.5; Gradle. | Línea actual con más soporte futuro; Maven es el estándar en Spring. |
| 2026-10-28 | Frontend React + Vite + Tailwind (TypeScript). | — | Elección del usuario. |
| 2026-10-28 | SQLite con SQL portable a PostgreSQL vía Flyway. | PostgreSQL; MySQL/MariaDB. | Arranque simple; migración futura a PostgreSQL prevista. |
| 2026-10-28 | Autoría de git humana, sin trailer de IA. | Trailer Co-Authored-By de IA. | Regla fija de rsc ratificada por el usuario. |
| 2026-10-28 | Réplica clean-room de Treinta con marca y código propios. | — | Evitar infracción de copyright, marca y términos de uso. |
| 2026-10-28 | Primera entrega dividida en dos specs: cuentas-y-negocio y libro-ventas-gastos-balance. | Una spec única; solo libro sin cuentas. | Subsistemas independientes; el libro depende de cuentas. |
| 2026-10-28 | Login email + contraseña; 1 dueño, 1 negocio; ventas libres y con productos (sin stock). | Celular+SMS; varios negocios; solo venta libre. | Elección del usuario. |
| 2026-10-28 | Cadena SDD en autopiloto; código en la rama claude/eager-johnson-chf30t. | Aprobación por fase; rama nueva. | Elección del usuario. |
| 2026-10-28 | Constitución v1.1.0: dinero en BIGINT centavos (principio 23 sustituye al 10). | TEXT decimal; PostgreSQL ya. | SQLite no tiene decimal exacto. Ratificado por el usuario. |
| 2026-10-28 | Persistencia con JdbcClient + Flyway, sin JPA. | JPA + dialecto comunitario SQLite. | SQL explícito y portable; sin depender de un dialecto no oficial. |
| 2026-10-28 | Sesión opaca en cookie HttpOnly SameSite=Strict con hash en BD. | JWT. | El cierre de sesión invalida de verdad (criterio de la spec). |
| 2026-10-28 | Venta con productos guarda copia de nombre y precio por línea. | Referencia viva al producto. | El historial no cambia al editar o borrar productos. |
| 2026-10-28 | Líneas de venta sin producto (nombre + precio) permitidas en la API. | Bloquear la edición de ventas con productos borrados. | Al editar no se pierden líneas; hallazgo de verify. |
| 2026-10-28 | TypeScript 6.0.3 en lugar de 7.0. | TypeScript 7. | typescript-eslint 8 aún no soporta TypeScript 7. |
| 2026-10-28 | Siguiente módulo: Inventario, en autopiloto, en esta rama; antes, layout web de escritorio (FTD). | Deudas/fiados. | Elección del usuario; se apoya en productos y ventas. |
| 2026-10-28 | Inventario escucha `SaleItemsChanged` del libro de forma síncrona en la misma transacción. | Listener asíncrono de Modulith; llamada directa del libro al inventario. | Consistencia fuerte sin acoplar el libro al inventario. |
| 2026-10-28 | Siguiente módulo: Deudas y fiados, autopiloto, misma rama. | PR primero. | "continúa" del usuario. |
| 2026-10-28 | "Fiado" como método de pago (CREDIT) y balance de caja (fiado no es ingreso hasta el abono). | Flag `paid`; balance por lo vendido. | Encaja en el flujo existente; responde "¿cuánto dinero me quedó?". |
| 2026-10-28 | Deudas en `ledger`; `customers` es directorio y su borrado se veta con un evento síncrono. | Deudas en `customers`. | Evita ciclo entre módulos. |
