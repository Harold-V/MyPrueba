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
