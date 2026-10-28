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
